<template>
  <el-dialog :model-value="modelValue" :title="meter ? '修改区域/用户' : '新增区域/用户'" width="560px"
             destroy-on-close @update:model-value="(v) => $emit('update:modelValue', v)" @open="onOpen">
    <el-form :model="form" label-width="110px">
      <el-form-item label="区域" required>
        <el-autocomplete v-model="form.area" :fetch-suggestions="suggest" placeholder="如 2层D区、9层A区" clearable
                         style="width: 100%" />
      </el-form-item>
      <el-form-item label="用户名 / 租户">
        <el-input v-model="form.customerName" placeholder="抄表表上的承租方简称，如 云山快仓" />
      </el-form-item>
      <el-form-item v-if="!meter" label="表计类型" required>
        <el-checkbox-group v-model="form.types">
          <el-checkbox value="电">电表</el-checkbox>
          <el-checkbox value="水">水表</el-checkbox>
        </el-checkbox-group>
        <div class="tip">垃圾费按区域的计租面积分摊，只要该区域有一块表就会进入分摊。</div>
      </el-form-item>
      <el-form-item v-if="showRatio" label="电表倍率">
        <el-input-number v-model="form.ratio" :min="0.01" :precision="2" />
        <span class="tip inline">园区电表多为 30 倍，个别 80 倍；水表倍率为 1。</span>
      </el-form-item>
      <el-form-item label="参与公摊">
        <el-switch v-model="form.sharePublic" :active-value="1" :inactive-value="0" />
        <span class="tip inline">关掉只付自身用量，不摊公摊、不摊垃圾费（如室外充电桩、未租单元）。</span>
      </el-form-item>
      <el-form-item label="计租面积(㎡)">
        <el-input-number v-model="form.leaseArea" :min="0" :precision="2" :controls="false" style="width: 160px" />
        <span class="tip inline">垃圾费按它分摊；同区域已填过的会自动沿用。</span>
      </el-form-item>
      <el-form-item v-if="!meter" label="起始读数">
        <el-input-number v-model="form.lastReading" :min="0" :precision="2" :controls="false" style="width: 160px" />
        <span class="tip inline">新表当月的上期读数；以后每月自动取上个月的本期读数。</span>
      </el-form-item>
      <el-form-item label="所属房间">
        <el-select v-model="form.roomId" placeholder="可选；选了房间，租户按执行中合同自动带出" clearable filterable style="width: 100%">
          <el-option v-for="r in rooms" :key="r.id" :label="r.code + (r.roomNo ? ` (${r.roomNo})` : '')" :value="r.id" />
        </el-select>
        <div class="tip">不关联房间也能出通知单（按用户名），但确认出账时会跳过这一户。</div>
      </el-form-item>
      <el-form-item v-if="!meter && form.types.length === 1" label="表计编号">
        <el-input v-model="form.code" placeholder="不填则自动生成" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { feePeriodApi } from '@/api/energy'
import { roomApi } from '@/api/building'

// meter 有值 = 修改;defaultTypes = 新增时默认勾选的表计类型;areas = 已有区域,做输入提示
const props = defineProps({
  modelValue: Boolean,
  meter: { type: Object, default: null },
  defaultTypes: { type: Array, default: () => ['电'] },
  areas: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const saving = ref(false)
const rooms = ref([])
const form = reactive({ area: '', customerName: '', types: ['电'], ratio: 30, sharePublic: 1, leaseArea: null, lastReading: 0, roomId: null, code: '' })
const showRatio = computed(() => (props.meter ? props.meter.energyType === '电' : form.types.includes('电')))

function onOpen() {
  const m = props.meter
  Object.assign(form, m
    ? { area: m.area || '', customerName: m.customerName || '', types: [m.energyType], ratio: Number(m.ratio || 1), sharePublic: m.sharePublic === 0 ? 0 : 1, leaseArea: m.leaseArea ?? null, lastReading: 0, roomId: m.roomId ?? null, code: '' }
    : { area: '', customerName: '', types: [...props.defaultTypes], ratio: 30, sharePublic: 1, leaseArea: null, lastReading: 0, roomId: null, code: '' })
  loadRooms()
}

let roomsLoaded = false
async function loadRooms() {
  if (roomsLoaded) return
  roomsLoaded = true
  try {
    rooms.value = (await roomApi.page({ pageNo: 1, pageSize: 500 }))?.records || []
  } catch (e) {
    roomsLoaded = false
  }
}

function suggest(query, cb) {
  const q = (query || '').trim()
  cb([...new Set(props.areas)].filter((a) => a && a.includes(q)).map((value) => ({ value })))
}

async function submit() {
  if (!form.area?.trim()) return ElMessage.warning('请填写区域')
  if (!props.meter && !form.types.length) return ElMessage.warning('请至少选一种表计类型')
  saving.value = true
  try {
    if (props.meter) {
      await feePeriodApi.saveMeter({
        id: props.meter.id ?? props.meter.meterId, area: form.area, customerName: form.customerName,
        ratio: showRatio.value ? form.ratio : null, sharePublic: form.sharePublic, leaseArea: form.leaseArea, roomId: form.roomId
      })
    } else {
      for (const type of form.types) {
        await feePeriodApi.saveMeter({
          energyType: type, area: form.area, customerName: form.customerName,
          code: form.types.length === 1 ? form.code : null,
          ratio: type === '电' ? form.ratio : 1, sharePublic: form.sharePublic, leaseArea: form.leaseArea,
          lastReading: form.lastReading, roomId: form.roomId
        })
      }
    }
    ElMessage.success('已保存，本月的公摊、垃圾费与通知单需要重新生成')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.tip { margin-top: 4px; font-size: 12px; line-height: 1.5; color: var(--el-text-color-secondary); }
.tip.inline { margin: 0 0 0 10px; }
</style>
