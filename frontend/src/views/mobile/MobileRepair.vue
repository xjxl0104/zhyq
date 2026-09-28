<template>
  <div class="m-repair">
    <header class="hd">
      <div class="hd-title">园区报修</div>
      <div class="hd-sub">填几项就行，物业收到后会尽快联系您</div>
    </header>

    <div class="tabs">
      <button :class="['tab', tab === 'submit' && 'on']" @click="tab = 'submit'">我要报修</button>
      <button :class="['tab', tab === 'my' && 'on']" @click="switchMy">进度查询</button>
    </div>

    <!-- 报修 -->
    <form v-if="tab === 'submit'" class="card" @submit.prevent="submit">
      <label class="field">
        <span class="lb">报修内容 <i>*</i></span>
        <input v-model="form.title" maxlength="100" placeholder="如：六楼A区女厕漏水" />
      </label>
      <label class="field">
        <span class="lb">位置</span>
        <input v-model="form.location" maxlength="100" placeholder="如：6号楼3层 302" />
      </label>
      <div class="field">
        <span class="lb">类别</span>
        <div class="chips">
          <button
            v-for="c in CATEGORIES"
            :key="c"
            type="button"
            :class="['chip', form.category === c && 'on']"
            @click="form.category = form.category === c ? '' : c"
          >{{ c }}</button>
        </div>
      </div>
      <div class="field">
        <span class="lb">紧急程度</span>
        <div class="chips">
          <button
            v-for="u in URGENCY"
            :key="u.value"
            type="button"
            :class="['chip', form.urgency === u.value && 'on']"
            @click="form.urgency = u.value"
          >{{ u.label }}</button>
        </div>
      </div>
      <label class="field">
        <span class="lb">您的称呼 <i>*</i></span>
        <input v-model="form.contact" maxlength="32" placeholder="如：张女士" />
      </label>
      <label class="field">
        <span class="lb">联系电话 <i>*</i></span>
        <input v-model="form.contactPhone" type="tel" maxlength="11" inputmode="numeric" placeholder="维修人员联系您用" />
      </label>
      <label class="field">
        <span class="lb">补充说明</span>
        <textarea v-model="form.remark" maxlength="500" rows="3" placeholder="方便上门的时间、具体情况等" />
      </label>

      <p v-if="error" class="err">{{ error }}</p>
      <button class="primary" type="submit" :disabled="submitting">{{ submitting ? '提交中…' : '提交报修' }}</button>
      <p class="tip">提交后可在「进度查询」里用同一手机号查看处理进度</p>
    </form>

    <!-- 进度查询 -->
    <div v-else class="card">
      <label class="field">
        <span class="lb">手机号</span>
        <input v-model="queryPhone" type="tel" maxlength="11" inputmode="numeric" placeholder="填报修时留的手机号" />
      </label>
      <p v-if="error" class="err">{{ error }}</p>
      <button class="primary" :disabled="querying" @click="loadMy">{{ querying ? '查询中…' : '查询' }}</button>

      <ul v-if="myList.length" class="list">
        <li v-for="o in myList" :key="o.code" class="item">
          <div class="item-hd">
            <span class="item-title">{{ o.title }}</span>
            <span :class="['badge', statusClass(o.status)]">{{ statusText(o.status) }}</span>
          </div>
          <div class="item-meta">工单号 {{ o.code }}</div>
          <div v-if="o.location" class="item-meta">位置 {{ o.location }}</div>
          <div class="item-meta">提交 {{ o.createTime || '-' }}</div>
          <div v-if="o.finishTime" class="item-meta">完成 {{ o.finishTime }}</div>
        </li>
      </ul>
      <p v-else-if="queried" class="tip">没有查到这个手机号的报修记录</p>
    </div>

    <!-- 提交成功 -->
    <div v-if="doneCode" class="mask" @click.self="doneCode = ''">
      <div class="done">
        <div class="done-icon">✓</div>
        <div class="done-title">已提交</div>
        <div class="done-code">工单号 {{ doneCode }}</div>
        <p class="tip">物业已收到，会尽快安排处理。可用手机号查询进度。</p>
        <button class="primary" @click="doneCode = ''">知道了</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { repairApi } from '@/api/publicRepair'

const CATEGORIES = ['水电', '空调', '门窗', '电梯', '照明', '消防', '保洁', '其他']
const URGENCY = [
  { value: 1, label: '不着急' },
  { value: 2, label: '一般' },
  { value: 3, label: '很急' }
]
// 与后台工单状态一一对应：1待派单 2待接单 3处理中 4待验收 5已完成 6已关闭 7已超时
const STATUS = {
  1: '已受理', 2: '待接单', 3: '处理中', 4: '待验收', 5: '已完成', 6: '已关闭', 7: '处理超时'
}
const PHONE_RE = /^1[3-9]\d{9}$/
const statusText = (s) => STATUS[s] || '处理中'
const statusClass = (s) => (s === 5 ? 'ok' : s === 7 ? 'warn' : 'doing')

const tab = ref('submit')
const error = ref('')
const submitting = ref(false)
const doneCode = ref('')
const emptyForm = () => ({ title: '', location: '', category: '', urgency: 2, contact: '', contactPhone: '', remark: '' })
const form = reactive(emptyForm())

async function submit() {
  error.value = ''
  if (!form.title.trim()) return (error.value = '请填写报修内容')
  if (!form.contact.trim()) return (error.value = '请填写您的称呼')
  if (!PHONE_RE.test(form.contactPhone.trim())) return (error.value = '请填写正确的手机号')
  submitting.value = true
  try {
    const phone = form.contactPhone.trim()
    const contact = form.contact.trim()
    const res = await repairApi.submit({ ...form, contact, contactPhone: phone })
    doneCode.value = res.code
    queryPhone.value = phone   // 查进度时不用再输一遍
    myList.value = []
    // 姓名与手机号留着，同一个人连着报两单不用重填
    Object.assign(form, emptyForm(), { contact, contactPhone: phone })
  } catch (e) {
    error.value = e?.message || '提交失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}

const queryPhone = ref('')
const myList = ref([])
const querying = ref(false)
const queried = ref(false)
function switchMy() {
  tab.value = 'my'
  error.value = ''
  if (queryPhone.value && !myList.value.length) loadMy()
}
async function loadMy() {
  error.value = ''
  if (!PHONE_RE.test(queryPhone.value.trim())) return (error.value = '请填写正确的手机号')
  querying.value = true
  try {
    myList.value = await repairApi.my(queryPhone.value.trim())
    queried.value = true
  } catch (e) {
    error.value = e?.message || '查询失败，请稍后再试'
  } finally {
    querying.value = false
  }
}
</script>

<style scoped>
.m-repair { min-height: 100vh; background: #f5f6f8; padding-bottom: 32px; }
.hd { padding: 24px 20px 18px; background: linear-gradient(135deg, #4c5ce0, #6f5bd6); color: #fff; }
.hd-title { font-size: 22px; font-weight: 600; }
.hd-sub { margin-top: 6px; font-size: 13px; opacity: .85; }
.tabs { display: flex; gap: 8px; padding: 12px 16px 0; }
.tab { flex: 1; padding: 10px 0; border: none; border-radius: 8px 8px 0 0; background: #eceef3; color: #606266; font-size: 15px; }
.tab.on { background: #fff; color: #4c5ce0; font-weight: 600; }
.card { margin: 0 16px; background: #fff; border-radius: 0 0 12px 12px; padding: 16px; }
.field { display: block; margin-bottom: 14px; }
.lb { display: block; font-size: 14px; color: #303133; margin-bottom: 6px; }
.lb i { color: #f56c6c; font-style: normal; }
input, textarea { width: 100%; box-sizing: border-box; padding: 11px 12px; border: 1px solid #dcdfe6;
  border-radius: 8px; font-size: 16px; color: #303133; background: #fff; font-family: inherit; }
input:focus, textarea:focus { outline: none; border-color: #4c5ce0; }
.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chip { padding: 8px 14px; border: 1px solid #dcdfe6; border-radius: 16px; background: #fff; font-size: 14px; color: #606266; }
.chip.on { border-color: #4c5ce0; color: #4c5ce0; background: #eef0fd; }
.primary { width: 100%; padding: 13px 0; border: none; border-radius: 8px; background: #4c5ce0;
  color: #fff; font-size: 16px; font-weight: 500; }
.primary:disabled { opacity: .6; }
.err { color: #f56c6c; font-size: 13px; margin: 0 0 10px; }
.tip { color: #909399; font-size: 12px; text-align: center; margin: 12px 0 0; line-height: 1.6; }
.list { list-style: none; margin: 16px 0 0; padding: 0; }
.item { border-top: 1px solid #f0f1f5; padding: 12px 0; }
.item-hd { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.item-title { font-size: 15px; color: #303133; font-weight: 500; }
.item-meta { font-size: 12px; color: #909399; margin-top: 4px; }
.badge { flex-shrink: 0; padding: 3px 9px; border-radius: 10px; font-size: 12px; }
.badge.ok { background: #e7f6ec; color: #3aa76d; }
.badge.doing { background: #eef0fd; color: #4c5ce0; }
.badge.warn { background: #fdefe6; color: #e6873a; }
.mask { position: fixed; inset: 0; background: rgba(0, 0, 0, .45); display: flex; align-items: center; justify-content: center; padding: 24px; }
.done { background: #fff; border-radius: 14px; padding: 24px 20px; width: 100%; max-width: 320px; text-align: center; }
.done-icon { width: 48px; height: 48px; line-height: 48px; margin: 0 auto 12px; border-radius: 50%;
  background: #e7f6ec; color: #3aa76d; font-size: 26px; }
.done-title { font-size: 18px; font-weight: 600; color: #303133; }
.done-code { margin-top: 6px; font-size: 14px; color: #606266; }
.done .primary { margin-top: 16px; }
</style>
