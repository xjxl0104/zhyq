<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="中介名称">
          <el-input v-model="query.name" placeholder="请输入中介名称" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="query.contact" placeholder="请输入联系人" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item label="中介类型">
          <el-select v-model="query.agencyType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="t in AGENCY_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="合作等级">
          <el-select v-model="query.grade" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="g in AGENCY_GRADE_OPTIONS" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="query.ownerName" placeholder="对接负责人" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="合作中" :value="1" />
            <el-option label="暂停合作" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增中介</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="agencyNo" label="中介编号" width="100">
          <template #default="{ row }">{{ row.agencyNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="name" label="中介名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="agencyType" label="类型" width="120" />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column label="合作等级" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.grade" :type="agencyGradeType(row.grade)" size="small">{{ row.grade }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="佣金比例" width="90" align="right">
          <template #default="{ row }">{{ row.commissionRate ?? 0 }}%</template>
        </el-table-column>
        <el-table-column label="协议" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.agreementSigned === 1 ? 'success' : 'info'" size="small">
              {{ row.agreementSigned === 1 ? '已签' : '未签' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="推荐/成交" width="95" align="center">
          <template #default="{ row }">{{ row.referralCount ?? 0 }} / {{ row.dealCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="90" />
        <el-table-column label="最近跟进" width="110">
          <template #default="{ row }">{{ row.lastFollowDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="跟进次数" width="85" align="center">
          <template #default="{ row }">{{ row.followCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="下次跟进" width="160">
          <template #default="{ row }">{{ row.nextFollow || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '合作中' : '暂停合作' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openFollow(row)">跟进记录</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 表单弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="760px">
      <el-form :model="form" label-width="110px" ref="formRef" :rules="rules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="中介名称" prop="name">
              <el-input v-model="form.name" placeholder="公司名或经纪人姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="中介类型" prop="agencyType">
              <el-select v-model="form.agencyType" placeholder="请选择" style="width: 100%">
                <el-option v-for="t in AGENCY_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="微信号"><el-input v-model="form.wechat" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所在地区"><el-input v-model="form.region" placeholder="如：杭州余杭" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="办公地址"><el-input v-model="form.address" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="擅长业务/资源">
              <el-input v-model="form.resourceDesc" placeholder="如：电商卖家资源多，擅长 500-2000㎡ 仓库租赁" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合作等级">
              <el-select v-model="form.grade" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="g in AGENCY_GRADE_OPTIONS" :key="g" :label="g" :value="g" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="对接负责人"><el-input v-model="form.ownerName" placeholder="园区招商人员" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="佣金比例">
              <el-input-number v-model="form.commissionRate" :min="0" :max="100" :precision="2" :step="1" />
              <span class="unit">%</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合作协议">
              <el-radio-group v-model="form.agreementSigned">
                <el-radio :value="1">已签</el-radio>
                <el-radio :value="0">未签</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="累计推荐客户">
              <el-input-number v-model="form.referralCount" :min="0" :step="1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="累计成交客户">
              <el-input-number v-model="form.dealCount" :min="0" :step="1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">合作中</el-radio>
                <el-radio :value="0">暂停合作</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <ChannelFollowDrawer v-model="followVisible" :agency="followAgency" @saved="load" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { channelApi } from '@/api/crm'
import ChannelFollowDrawer from './ChannelFollowDrawer.vue'
import { AGENCY_TYPE_OPTIONS, AGENCY_GRADE_OPTIONS, agencyGradeType } from './agencyOptions'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const emptyQuery = () => ({ name: '', contact: '', agencyType: '', grade: '', ownerName: '', status: null })
const query = reactive({ pageNo: 1, pageSize: 10, ...emptyQuery() })

async function load() {
  loading.value = true
  try {
    const res = await channelApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.pageNo = 1
  load()
}
function reset() {
  Object.assign(query, { pageNo: 1, ...emptyQuery() })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({
  id: null, version: null, name: '', agencyType: '中介公司', contact: '', phone: '', wechat: '',
  region: '', address: '', resourceDesc: '', grade: '', ownerName: '', commissionRate: 0,
  agreementSigned: 0, referralCount: 0, dealCount: 0, status: 1, remark: ''
})
const form = reactive(emptyForm())
const rules = {
  name: [{ required: true, message: '请输入中介名称', trigger: 'blur' }],
  agencyType: [{ required: true, message: '请选择中介类型', trigger: 'change' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑中介' : '新增中介'
  Object.assign(form, emptyForm())
  if (row) {
    // 只取表单字段，编号与跟进统计由后端维护
    Object.keys(form).forEach((k) => { if (row[k] !== undefined && row[k] !== null) form[k] = row[k] })
  }
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await channelApi.update(form)
  else await channelApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await channelApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

const followVisible = ref(false)
const followAgency = ref(null)
function openFollow(row) {
  followAgency.value = row
  followVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.unit { margin-left: 8px; color: #909399; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
