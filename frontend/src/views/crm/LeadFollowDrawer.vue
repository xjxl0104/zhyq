<template>
  <el-drawer :model-value="modelValue" :title="drawerTitle" size="64%"
             @update:model-value="(v) => emit('update:modelValue', v)" @open="load">
    <div class="table-card">
      <div class="toolbar">
        <span class="section-title">
          回访跟进记录
          <span class="hint">— 每次电话、微信、上门后追加一条；保存后自动更新线索的最近跟进日期与累计次数</span>
        </span>
        <el-button type="primary" @click="openDialog"><el-icon><Plus /></el-icon>新增跟进</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="followNo" label="跟进编号" width="120" />
        <el-table-column prop="followDate" label="跟进日期" width="120" />
        <el-table-column prop="type" label="跟进方式" width="120" />
        <el-table-column prop="followBy" label="跟进人" width="100" />
        <el-table-column prop="content" label="沟通要点/客户反馈" min-width="240" show-overflow-tooltip />
        <el-table-column label="意向变化" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.intentChange" :type="intentType(row.intentChange)" size="small">
              {{ row.intentChange }}
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="issue" label="问题/需协调" min-width="180" show-overflow-tooltip />
        <el-table-column prop="nextFollow" label="下次跟进计划" width="170" />
        <el-table-column prop="result" label="跟进结果/待办" min-width="200" show-overflow-tooltip />
      </el-table>
      <div v-if="!loading && list.length === 0" class="empty-tip">还没有跟进记录，点右上角「新增跟进」开始</div>
    </div>

    <el-dialog v-model="dialogVisible" append-to-body title="新增跟进记录" width="720px">
      <el-form :model="form" label-width="120px" ref="formRef" :rules="rules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="跟进日期" prop="followDate">
              <el-date-picker v-model="form.followDate" type="date" value-format="YYYY-MM-DD"
                              placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="跟进方式" prop="type">
              <el-select v-model="form.type" placeholder="请选择" style="width: 100%">
                <el-option v-for="t in FOLLOW_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="跟进人">
              <el-input v-model="form.followBy" placeholder="如：小林" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户意向变化">
              <el-select v-model="form.intentChange" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="c in INTENT_CHANGE_OPTIONS" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="沟通要点/客户反馈" prop="content">
              <el-input v-model="form.content" type="textarea" :rows="3"
                        placeholder="客户说了什么、关心什么、报了什么价" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="遇到的问题/需协调">
              <el-input v-model="form.issue" type="textarea" :rows="2"
                        placeholder="如：需与仓库业主确认可否分租及底价" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下次跟进计划">
              <el-date-picker v-model="form.nextFollow" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                              placeholder="选择时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="跟进结果/待办">
              <el-input v-model="form.result" type="textarea" :rows="2"
                        placeholder="如：已发送分租方案报价单，7/25 前需二次跟进" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { followApi } from '@/api/crm'
import { FOLLOW_TYPE_OPTIONS, INTENT_CHANGE_OPTIONS } from './leadOptions'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  lead: { type: Object, default: null }
})
// saved 让父页面在关闭抽屉后刷新列表——线索的最近跟进日期/累计次数由后端回写，需要重新拉取
const emit = defineEmits(['update:modelValue', 'saved'])

const leadId = computed(() => props.lead?.id || null)
const drawerTitle = computed(() =>
  props.lead ? `跟进记录 · ${props.lead.leadNo || ''} ${props.lead.contact || ''}` : '跟进记录')

const intentType = (c) => {
  if (c === '明显升温' || c === '已成交') return 'success'
  if (c === '降温' || c === '已流失') return 'danger'
  return 'info'
}

const loading = ref(false)
const list = ref([])

async function load() {
  if (!leadId.value) return
  loading.value = true
  try {
    list.value = await followApi.list(leadId.value)
  } finally {
    loading.value = false
  }
}

// 换一条线索再打开时清空，避免闪现上一条的记录
watch(leadId, () => { list.value = [] })

const formRef = ref()
const dialogVisible = ref(false)
const saving = ref(false)
const today = () => new Date().toISOString().slice(0, 10)
const defaultForm = () => ({
  followDate: today(), type: '电话', followBy: '', intentChange: '',
  content: '', issue: '', nextFollow: '', result: ''
})
const form = reactive(defaultForm())
const rules = {
  followDate: [{ required: true, message: '请选择跟进日期', trigger: 'change' }],
  type: [{ required: true, message: '请选择跟进方式', trigger: 'change' }],
  content: [{ required: true, message: '请填写沟通要点', trigger: 'blur' }]
}

function openDialog() {
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    await followApi.add({ ...form, leadId: leadId.value })
    ElMessage.success('跟进记录已保存')
    dialogVisible.value = false
    await load()
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.section-title { font-size: 15px; font-weight: 600; color: #303133; }
.hint { font-size: 13px; font-weight: 400; color: #909399; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.empty-tip { text-align: center; color: #909399; padding: 16px 0; }
</style>
