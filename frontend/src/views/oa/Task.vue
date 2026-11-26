<template>
  <div class="task-page">
    <!-- 顶部主视图切换 -->
    <div class="top-header">
      <div class="header-left">
        <el-radio-group v-model="viewMode">
          <el-radio-button value="matrix">年度周期任务年月表</el-radio-button>
          <el-radio-button value="adhoc">临时 / 单次任务</el-radio-button>
        </el-radio-group>
        <span class="header-desc" v-if="viewMode === 'matrix'">
          直观呈现消防检测、电梯维保、配电巡检、化粪池清理等周期性任务与每月节点的关系
        </span>
      </div>
      <div class="header-right" v-if="viewMode === 'matrix'">
        <el-button-group>
          <el-button @click="changeYear(-1)"><el-icon><ArrowLeft /></el-icon></el-button>
          <el-button class="year-btn" @click="year = currentYear">{{ year }} 年</el-button>
          <el-button @click="changeYear(1)"><el-icon><ArrowRight /></el-icon></el-button>
        </el-button-group>
        <el-button type="primary" @click="openTaskDlg()"><el-icon><Plus /></el-icon>新增周期任务</el-button>
      </div>
      <div class="header-right" v-else>
        <el-button type="primary" @click="openAdhocDialog()"><el-icon><Plus /></el-icon>新增临时任务</el-button>
      </div>
    </div>

    <!-- ==================== 视图一：年度周期任务可视化年月表 ==================== -->
    <template v-if="viewMode === 'matrix'">
      <!-- 年度指标卡片 -->
      <div class="kpi-strip">
        <div class="kpi-card">
          <span class="kpi-label">周期任务项</span>
          <strong class="kpi-val">{{ filteredTasks.length }} <small>项</small></strong>
        </div>
        <div class="kpi-card">
          <span class="kpi-label">{{ year }}年计划节点</span>
          <strong class="kpi-val">{{ kpi.total }} <small>个</small></strong>
        </div>
        <div class="kpi-card kpi-done">
          <span class="kpi-label">已完成节点 / 达成率</span>
          <strong class="kpi-val">{{ kpi.done }} <small>({{ kpi.rate }}%)</small></strong>
        </div>
        <div class="kpi-card kpi-cur">
          <span class="kpi-label">{{ focusedMonth }}月待执行节点</span>
          <strong class="kpi-val">{{ kpi.focusedPending }} <small>个</small></strong>
        </div>
        <div class="kpi-card" :class="{ 'kpi-warn': kpi.overdue > 0 }">
          <span class="kpi-label">逾期 / 异常节点</span>
          <strong class="kpi-val">{{ kpi.overdue }} <small>个</small></strong>
        </div>
      </div>

      <!-- 筛选与图例 -->
      <div class="filter-legend-bar">
        <div class="filters">
          <el-select v-model="filterCategory" placeholder="全部分类" clearable size="small" style="width: 130px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select v-model="filterCycle" placeholder="全部周期" clearable size="small" style="width: 130px">
            <el-option v-for="(label, code) in cycleLabels" :key="code" :label="label" :value="code" />
          </el-select>
          <el-input v-model="filterKeyword" placeholder="搜索任务名 / 负责人 / 维保单位" clearable size="small" style="width: 220px" />
        </div>
        <div class="legend">
          <span class="leg-item"><i class="dot done">✓</i>已完成</span>
          <span class="leg-item"><i class="dot doing">●</i>进行中</span>
          <span class="leg-item"><i class="dot planned">○</i>计划节点</span>
          <span class="leg-item"><i class="dot overdue">!</i>逾期/异常</span>
          <span class="leg-tip">点击任意节点即可打卡完成或上传检测报告</span>
        </div>
      </div>

      <!-- 12个月可视化任务节点矩阵 -->
      <div class="matrix-card" v-loading="boardLoading">
        <div class="matrix-scroll">
          <table class="matrix-table">
            <thead>
              <tr>
                <th class="task-col sticky-left">周期任务 / 频次 / 负责人</th>
                <th
                  v-for="m in 12"
                  :key="m"
                  class="month-col"
                  :class="{
                    'is-current': year === currentYear && m === currentMonth,
                    'is-focused': m === focusedMonth
                  }"
                  @click="focusedMonth = m"
                >
                  <div class="m-head">
                    <span>{{ m }}月</span>
                    <span v-if="year === currentYear && m === currentMonth" class="cur-pill">本月</span>
                  </div>
                </th>
                <th class="stat-col">年度进度</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in filteredTasks" :key="task.id" class="task-row">
                <td class="task-cell sticky-left">
                  <div class="task-meta">
                    <span class="color-bar" :style="{ background: task.color || '#4F46E5' }"></span>
                    <div class="task-text">
                      <div class="task-name-row">
                        <span class="task-name" :title="task.name">{{ task.name }}</span>
                        <span class="row-ops">
                          <el-button link type="primary" size="small" @click="openTaskDlg(task)">编辑</el-button>
                          <el-popconfirm title="确认删除该周期任务及其执行记录？" @confirm="removeTask(task.id)">
                            <template #reference><el-button link type="danger" size="small">删</el-button></template>
                          </el-popconfirm>
                        </span>
                      </div>
                      <div class="task-tags">
                        <span class="cycle-pill" :style="{ color: task.color || '#4F46E5', borderColor: (task.color || '#4F46E5') + '55', background: (task.color || '#4F46E5') + '12' }">
                          {{ cycleLabels[task.cycleType] || task.cycleType }}
                        </span>
                        <span class="sub-chip">{{ task.category }}</span>
                        <span class="sub-chip">{{ task.nodeDay || 15 }}日前</span>
                      </div>
                      <div class="task-owner" v-if="task.owner || task.vendor">
                        {{ [task.owner, task.vendor].filter(Boolean).join(' · ') }}
                      </div>
                    </div>
                  </div>
                </td>

                <td
                  v-for="m in 12"
                  :key="m"
                  class="node-cell"
                  :class="{
                    'is-current': year === currentYear && m === currentMonth,
                    'is-focused': m === focusedMonth
                  }"
                >
                  <!-- 水平时间轴连线：连接同一任务的首尾计划月 -->
                  <span
                    v-if="hasLeftTrack(task, m)"
                    class="track track-left"
                    :style="{ background: (task.color || '#4F46E5') + '45' }"
                  />
                  <span
                    v-if="hasRightTrack(task, m)"
                    class="track track-right"
                    :style="{ background: (task.color || '#4F46E5') + '45' }"
                  />

                  <el-tooltip
                    v-if="isScheduled(task, m) || getLog(task.id, m)"
                    placement="top"
                    :show-after="200"
                  >
                    <template #content>
                      <div class="tip-box">
                        <div><b>{{ task.name }}</b>（{{ year }}年{{ m }}月）</div>
                        <div>节点状态：{{ nodeStateInfo(task, m).label }}</div>
                        <div>计划时间：{{ year }}-{{ String(m).padStart(2, '0') }}-{{ String(task.nodeDay || 15).padStart(2, '0') }}</div>
                        <div v-if="getLog(task.id, m)?.completedDate">完成日期：{{ getLog(task.id, m).completedDate }}</div>
                        <div v-if="getLog(task.id, m)?.executor">执行人：{{ getLog(task.id, m).executor }}</div>
                        <div v-if="getLog(task.id, m)?.remark">备注：{{ getLog(task.id, m).remark }}</div>
                      </div>
                    </template>
                    <button
                      type="button"
                      class="node-btn"
                      :class="nodeStateInfo(task, m).cls"
                      :style="nodeBtnStyle(task, m)"
                      @click="openNodeDrawer(task, m)"
                    >
                      <span class="node-icon">{{ nodeStateInfo(task, m).icon }}</span>
                      <span class="node-day">{{ task.nodeDay || 15 }}日</span>
                      <span v-if="getLog(task.id, m)?.attachmentCount > 0" class="att-dot" title="已有附件">📎</span>
                    </button>
                  </el-tooltip>

                  <button
                    v-else
                    type="button"
                    class="empty-add-btn"
                    title="临时补录本月执行记录"
                    @click="openNodeDrawer(task, m)"
                  >+</button>
                </td>

                <td class="stat-cell">
                  <div class="row-stat-text">{{ taskProgress(task).done }} / {{ taskProgress(task).total }}</div>
                  <div class="mini-bar">
                    <div class="mini-bar-fill" :style="{ width: taskProgress(task).pct + '%', background: task.color || '#4F46E5' }" />
                  </div>
                </td>
              </tr>

              <tr v-if="!filteredTasks.length">
                <td colspan="14" class="empty-row">暂无符合条件的周期任务，请点击右上角「新增周期任务」</td>
              </tr>
            </tbody>
            <tfoot v-if="filteredTasks.length">
              <tr class="summary-row">
                <td class="sticky-left summary-title">月度任务负荷（完成 / 计划）</td>
                <td
                  v-for="m in 12"
                  :key="m"
                  class="summary-cell"
                  :class="{ 'is-focused': m === focusedMonth }"
                  @click="focusedMonth = m"
                >
                  <div class="sum-num">
                    <b>{{ monthStats[m].done }}</b>/{{ monthStats[m].total }}
                  </div>
                  <div class="mini-bar">
                    <div class="mini-bar-fill sum-fill" :style="{ width: monthStats[m].pct + '%' }" />
                  </div>
                </td>
                <td class="summary-cell">
                  <b>{{ kpi.done }}/{{ kpi.total }}</b>
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      <!-- 聚焦月份节点清单 -->
      <div class="focus-panel">
        <div class="focus-head">
          <div class="focus-title">
            <span>{{ year }}年 {{ focusedMonth }}月 任务节点清单</span>
            <el-tag size="small" type="primary" effect="plain">共 {{ focusedMonthNodes.length }} 个节点</el-tag>
          </div>
          <div class="focus-hint">点击上方任意月份表头可切换查看当月任务节点清单</div>
        </div>
        <div v-if="focusedMonthNodes.length" class="focus-grid">
          <div v-for="item in focusedMonthNodes" :key="item.task.id" class="focus-card">
            <div class="fc-top">
              <span class="fc-dot" :style="{ background: item.task.color || '#4F46E5' }" />
              <strong class="fc-name">{{ item.task.name }}</strong>
              <el-tag size="small" :type="item.state.tagType">{{ item.state.label }}</el-tag>
            </div>
            <div class="fc-meta">
              <span>{{ cycleLabels[item.task.cycleType] }} · {{ item.task.nodeDay || 15 }}日前</span>
              <span v-if="item.task.owner">负责：{{ item.task.owner }}</span>
              <span v-if="item.task.vendor">单位：{{ item.task.vendor }}</span>
            </div>
            <div class="fc-actions">
              <el-button
                v-if="item.state.code !== 2"
                size="small"
                type="success"
                plain
                @click="quickComplete(item.task, focusedMonth)"
              >一键打卡完成</el-button>
              <el-button size="small" type="primary" link @click="openNodeDrawer(item.task, focusedMonth)">
                {{ item.log?.attachmentCount ? `查看记录 / 附件(${item.log.attachmentCount})` : '填写记录 / 上传报告' }}
              </el-button>
            </div>
          </div>
        </div>
        <div v-else class="focus-empty">{{ year }}年 {{ focusedMonth }}月 暂无计划节点</div>
      </div>
    </template>

    <!-- ==================== 视图二：临时 / 单次任务（保留原有功能） ==================== -->
    <template v-else>
      <div class="search-bar">
        <el-form :inline="true" :model="adhocQuery">
          <el-form-item label="任务标题"><el-input v-model="adhocQuery.title" placeholder="请输入标题" clearable style="width: 180px" /></el-form-item>
          <el-form-item label="负责人"><el-input v-model="adhocQuery.owner" placeholder="请输入负责人" clearable style="width: 150px" /></el-form-item>
          <el-form-item label="状态">
            <el-select v-model="adhocQuery.status" placeholder="全部" clearable style="width: 130px">
              <el-option v-for="(label, val) in adhocStatusMap" :key="val" :label="label" :value="Number(val)" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadAdhoc"><el-icon><Search /></el-icon>查询</el-button>
            <el-button @click="resetAdhoc">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      <div class="table-card">
        <el-table :data="adhocList" v-loading="adhocLoading" border stripe>
          <el-table-column type="index" label="序号" width="65" />
          <el-table-column prop="title" label="任务标题" min-width="180" />
          <el-table-column prop="owner" label="负责人" min-width="100" />
          <el-table-column label="优先级" width="90">
            <template #default="{ row }"><el-tag :type="priorityTagType(row.priority)">{{ priorityMap[row.priority] || '-' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="source" label="来源" min-width="100" />
          <el-table-column prop="dueDate" label="截止时间" width="170" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag :type="adhocStatusTag(row.status)">{{ adhocStatusMap[row.status] || '未知' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" :disabled="row.status === 3" @click="doneAdhoc(row.id)">完成</el-button>
              <el-button link type="primary" @click="openAdhocDialog(row)">编辑</el-button>
              <el-popconfirm title="确认删除?" @confirm="removeAdhoc(row.id)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                       :total="adhocTotal" v-model:current-page="adhocQuery.pageNo"
                       v-model:page-size="adhocQuery.pageSize" :page-sizes="[10,20,50]" @change="loadAdhoc" />
      </div>
    </template>

    <!-- 新增/编辑周期任务弹窗 -->
    <el-dialog v-model="taskDlg.visible" :title="taskForm.id ? '编辑周期任务' : '新增周期任务'" width="580px" destroy-on-close>
      <el-form ref="taskFormRef" :model="taskForm" :rules="taskRules" label-width="104px">
        <el-form-item label="任务名称" prop="name">
          <el-input v-model="taskForm.name" placeholder="如：消防设施月度检测、化粪池清理、电梯维保" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="任务分类">
              <el-select v-model="taskForm.category" allow-create filterable style="width: 100%">
                <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行周期">
              <el-select v-model="taskForm.cycleType" style="width: 100%" @change="onCycleChange">
                <el-option v-for="(label, code) in cycleLabels" :key="code" :label="label" :value="code" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="计划执行月份">
          <el-checkbox-group v-model="taskForm.months" class="months-grid">
            <el-checkbox v-for="m in 12" :key="m" :value="m">{{ m }}月</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="每月计划节点日">
              <el-input-number v-model="taskForm.nodeDay" :min="1" :max="28" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="节点标识颜色">
              <div class="color-picks">
                <span
                  v-for="c in colorPalette"
                  :key="c"
                  class="cp-dot"
                  :class="{ active: taskForm.color === c }"
                  :style="{ background: c }"
                  @click="taskForm.color = c"
                />
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="园区负责人"><el-input v-model="taskForm.owner" placeholder="如 工程消防主管" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="维保/检测单位"><el-input v-model="taskForm.vendor" placeholder="如 消防维保单位" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="任务标准说明">
          <el-input v-model="taskForm.description" type="textarea" :rows="2" placeholder="可选：验收标准、法规频次要求等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="taskDlg.saving" @click="saveTask">保存任务</el-button>
      </template>
    </el-dialog>

    <!-- 月度节点执行抽屉 -->
    <el-drawer v-model="nodeDrawer.visible" :title="nodeDrawerTitle" size="460px" destroy-on-close>
      <el-form :model="nodeForm" label-width="92px">
        <el-form-item label="任务名称">
          <strong>{{ nodeDrawer.task?.name }}</strong>
          <el-tag size="small" style="margin-left: 8px">{{ cycleLabels[nodeDrawer.task?.cycleType] }}</el-tag>
        </el-form-item>
        <el-form-item label="执行状态">
          <el-radio-group v-model="nodeForm.status">
            <el-radio-button :value="0">待执行</el-radio-button>
            <el-radio-button :value="1">进行中</el-radio-button>
            <el-radio-button :value="2">已完成</el-radio-button>
            <el-radio-button :value="3">异常/逾期</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="nodeForm.plannedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="完成日期">
          <el-date-picker v-model="nodeForm.completedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="完成后填写或自动回填今天" />
        </el-form-item>
        <el-form-item label="执行人/对接人">
          <el-input v-model="nodeForm.executor" placeholder="填写现场执行或验收人员" />
        </el-form-item>
        <el-form-item label="执行记录备注">
          <el-input v-model="nodeForm.remark" type="textarea" :rows="3" placeholder="填写检测结果、整改事项或维保单号" />
        </el-form-item>
        <el-form-item label="检测报告/照片">
          <FileUpload v-model="nodeFiles" biz-type="oa_task_node" :biz-id="nodeForm.id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDrawer.visible = false">取消</el-button>
        <el-button type="primary" :loading="nodeDrawer.saving" @click="saveNode">保存节点记录</el-button>
      </template>
    </el-drawer>

    <!-- 临时任务弹窗 -->
    <el-dialog v-model="adhocDlg.visible" :title="adhocDlg.title" width="520px">
      <el-form :model="adhocForm" label-width="90px" ref="adhocFormRef" :rules="adhocRules">
        <el-form-item label="任务标题" prop="title"><el-input v-model="adhocForm.title" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="adhocForm.owner" /></el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="adhocForm.priority" style="width: 100%">
            <el-option v-for="(label, val) in priorityMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="adhocForm.source" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="s in ['招商', '物业', '巡检', '手工']" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间"><el-date-picker v-model="adhocForm.dueDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="adhocForm.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="adhocForm.status" style="width: 100%">
            <el-option v-for="(label, val) in adhocStatusMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="附件"><FileUpload v-model="adhocFiles" biz-type="oa_task" :biz-id="adhocForm.id" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adhocDlg.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAdhoc">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Plus, Search } from '@element-plus/icons-vue'
import { periodicTaskApi, taskApi } from '@/api/oa'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

const now = new Date()
const currentYear = now.getFullYear()
const currentMonth = now.getMonth() + 1
const currentDay = now.getDate()

const viewMode = ref('matrix')
const year = ref(currentYear)
const focusedMonth = ref(currentMonth)
const boardLoading = ref(false)
const tasks = ref([])
const logs = ref([])

const categories = ['消防安全', '特种设备', '电气设施', '环境卫生', '安全检测', '设施维保', '综合管理']
const cycleLabels = { MONTHLY: '每月一次', QUARTERLY: '每季一次', HALF_YEAR: '半年一次', YEARLY: '每年一次', CUSTOM: '自定义月份' }
const colorPalette = ['#EF4444', '#2563EB', '#F59E0B', '#10B981', '#8B5CF6', '#EC4899', '#0EA5E9', '#4F46E5']

const filterCategory = ref('')
const filterCycle = ref('')
const filterKeyword = ref('')

function parseMonths(json) {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr.map(Number).filter((m) => m >= 1 && m <= 12).sort((a, b) => a - b) : []
  } catch {
    return []
  }
}

const logMap = computed(() => {
  const m = new Map()
  for (const l of logs.value) m.set(`${l.taskId}:${l.monthNo}`, l)
  return m
})
const getLog = (taskId, monthNo) => logMap.value.get(`${taskId}:${monthNo}`) || null

const filteredTasks = computed(() => {
  const kw = filterKeyword.value.trim().toLowerCase()
  return tasks.value.filter((t) => {
    if (filterCategory.value && t.category !== filterCategory.value) return false
    if (filterCycle.value && t.cycleType !== filterCycle.value) return false
    if (kw && ![t.name, t.owner, t.vendor, t.category].some((v) => String(v || '').toLowerCase().includes(kw))) return false
    return true
  })
})

function isScheduled(task, m) {
  return parseMonths(task.monthsJson).includes(m)
}
function hasLeftTrack(task, m) {
  const ms = parseMonths(task.monthsJson)
  return ms.length > 1 && m > ms[0] && m <= ms[ms.length - 1]
}
function hasRightTrack(task, m) {
  const ms = parseMonths(task.monthsJson)
  return ms.length > 1 && m >= ms[0] && m < ms[ms.length - 1]
}

function isPastDue(task, m) {
  if (year.value < currentYear) return true
  if (year.value > currentYear) return false
  if (m < currentMonth) return true
  if (m === currentMonth && currentDay > Number(task.nodeDay || 15)) return true
  return false
}

function nodeStateInfo(task, m) {
  const log = getLog(task.id, m)
  const st = log ? Number(log.status) : 0
  if (st === 2) return { code: 2, label: '已完成', icon: '✓', cls: 'st-done', tagType: 'success' }
  if (st === 1) return { code: 1, label: '进行中', icon: '●', cls: 'st-doing', tagType: 'primary' }
  if (st === 3 || (isScheduled(task, m) && isPastDue(task, m))) {
    return { code: 3, label: st === 3 ? '异常/逾期' : '已逾期', icon: '!', cls: 'st-overdue', tagType: 'danger' }
  }
  return { code: 0, label: '待执行', icon: '○', cls: 'st-planned', tagType: 'info' }
}

function nodeBtnStyle(task, m) {
  const info = nodeStateInfo(task, m)
  const color = task.color || '#4F46E5'
  if (info.code === 0) return { borderColor: color, color }
  return {}
}

function taskProgress(task) {
  const ms = parseMonths(task.monthsJson)
  const total = ms.length
  const done = ms.filter((m) => Number(getLog(task.id, m)?.status) === 2).length
  return { total, done, pct: total ? Math.round((done / total) * 100) : 0 }
}

const monthStats = computed(() => {
  const res = {}
  for (let m = 1; m <= 12; m++) {
    let total = 0
    let done = 0
    for (const t of filteredTasks.value) {
      if (isScheduled(t, m)) {
        total++
        if (Number(getLog(t.id, m)?.status) === 2) done++
      }
    }
    res[m] = { total, done, pct: total ? Math.round((done / total) * 100) : 0 }
  }
  return res
})

const kpi = computed(() => {
  let total = 0, done = 0, overdue = 0, focusedPending = 0
  for (const t of filteredTasks.value) {
    for (const m of parseMonths(t.monthsJson)) {
      total++
      const st = nodeStateInfo(t, m)
      if (st.code === 2) done++
      if (st.code === 3) overdue++
      if (m === focusedMonth.value && st.code !== 2) focusedPending++
    }
  }
  return { total, done, overdue, focusedPending, rate: total ? Math.round((done / total) * 100) : 0 }
})

const focusedMonthNodes = computed(() => {
  return filteredTasks.value
    .filter((t) => isScheduled(t, focusedMonth.value) || getLog(t.id, focusedMonth.value))
    .map((t) => ({ task: t, log: getLog(t.id, focusedMonth.value), state: nodeStateInfo(t, focusedMonth.value) }))
    .sort((a, b) => (a.task.nodeDay || 15) - (b.task.nodeDay || 15))
})

async function loadBoard() {
  boardLoading.value = true
  try {
    const res = await periodicTaskApi.board({ year: year.value })
    tasks.value = res?.tasks || []
    logs.value = res?.logs || []
  } finally {
    boardLoading.value = false
  }
}

function changeYear(delta) {
  year.value += delta
}
watch(year, loadBoard)

// ---- 周期任务新增/编辑 ----
const taskDlg = reactive({ visible: false, saving: false })
const taskFormRef = ref()
const taskForm = reactive({
  id: null, name: '', category: '消防安全', cycleType: 'MONTHLY',
  months: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
  nodeDay: 15, owner: '', vendor: '', color: '#EF4444', description: '', sort: 100
})
const taskRules = { name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }] }

function onCycleChange(type) {
  if (type === 'MONTHLY') taskForm.months = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
  else if (type === 'QUARTERLY') taskForm.months = [3, 6, 9, 12]
  else if (type === 'HALF_YEAR') taskForm.months = [6, 12]
  else if (type === 'YEARLY') taskForm.months = [12]
}

function openTaskDlg(row = null) {
  if (row) {
    Object.assign(taskForm, {
      id: row.id, name: row.name, category: row.category || '设施维保',
      cycleType: row.cycleType || 'MONTHLY', months: parseMonths(row.monthsJson),
      nodeDay: row.nodeDay || 15, owner: row.owner || '', vendor: row.vendor || '',
      color: row.color || '#4F46E5', description: row.description || '', sort: row.sort ?? 100
    })
  } else {
    Object.assign(taskForm, {
      id: null, name: '', category: '消防安全', cycleType: 'MONTHLY',
      months: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
      nodeDay: 15, owner: '', vendor: '', color: colorPalette[tasks.value.length % colorPalette.length],
      description: '', sort: (tasks.value.length + 1) * 10
    })
  }
  taskDlg.visible = true
}

async function saveTask() {
  await taskFormRef.value.validate()
  if (!taskForm.months.length) return ElMessage.warning('请至少勾选一个计划执行月份')
  taskDlg.saving = true
  try {
    const payload = {
      ...taskForm,
      monthsJson: JSON.stringify([...taskForm.months].sort((a, b) => a - b))
    }
    delete payload.months
    if (payload.id) await periodicTaskApi.update(payload)
    else await periodicTaskApi.add(payload)
    ElMessage.success('周期任务已保存')
    taskDlg.visible = false
    await loadBoard()
  } finally {
    taskDlg.saving = false
  }
}

async function removeTask(id) {
  await periodicTaskApi.remove(id)
  ElMessage.success('已删除')
  await loadBoard()
}

// ---- 节点执行抽屉 & 一键完成 ----
const nodeDrawer = reactive({ visible: false, saving: false, task: null, monthNo: 1 })
const nodeForm = reactive({ id: null, taskId: null, yearNo: currentYear, monthNo: 1, status: 0, plannedDate: null, completedDate: null, executor: '', remark: '' })
const nodeFiles = ref([])
const nodeDrawerTitle = computed(() => `${nodeDrawer.task?.name || ''} · ${year.value}年${nodeDrawer.monthNo}月节点`)

async function openNodeDrawer(task, monthNo) {
  nodeDrawer.task = task
  nodeDrawer.monthNo = monthNo
  const existing = getLog(task.id, monthNo)
  const planned = `${year.value}-${String(monthNo).padStart(2, '0')}-${String(Math.min(task.nodeDay || 15, 28)).padStart(2, '0')}`
  Object.assign(nodeForm, {
    id: existing?.id || null,
    taskId: task.id,
    yearNo: year.value,
    monthNo,
    status: existing ? Number(existing.status) : 0,
    plannedDate: existing?.plannedDate || planned,
    completedDate: existing?.completedDate || null,
    executor: existing?.executor || task.owner || '',
    remark: existing?.remark || ''
  })
  nodeFiles.value = []
  if (existing?.id) {
    try { nodeFiles.value = (await fileApi.list('oa_task_node', existing.id)) || [] } catch { /* ignore */ }
  }
  nodeDrawer.visible = true
}

async function saveNode() {
  nodeDrawer.saving = true
  try {
    const saved = await periodicTaskApi.upsertLog({ ...nodeForm })
    const logId = saved?.id || nodeForm.id
    const pendingIds = (nodeFiles.value || []).filter((f) => f && f.id && !f.bizId).map((f) => f.id)
    if (logId && pendingIds.length) {
      try { await fileApi.attach('oa_task_node', logId, pendingIds) } catch { /* ignore */ }
    }
    ElMessage.success('节点执行记录已更新')
    nodeDrawer.visible = false
    await loadBoard()
  } finally {
    nodeDrawer.saving = false
  }
}

async function quickComplete(task, monthNo) {
  const existing = getLog(task.id, monthNo)
  const planned = `${year.value}-${String(monthNo).padStart(2, '0')}-${String(Math.min(task.nodeDay || 15, 28)).padStart(2, '0')}`
  await periodicTaskApi.upsertLog({
    taskId: task.id,
    yearNo: year.value,
    monthNo,
    status: 2,
    plannedDate: existing?.plannedDate || planned,
    completedDate: new Date().toISOString().slice(0, 10),
    executor: existing?.executor || task.owner || '',
    remark: existing?.remark || '按期完成'
  })
  ElMessage.success(`「${task.name}」${monthNo}月节点已打卡完成`)
  await loadBoard()
}

// ---- 临时/单次任务（保留原有 oa_task 功能） ----
const adhocStatusMap = { 1: '待处理', 2: '处理中', 3: '已完成' }
const priorityMap = { 1: '低', 2: '中', 3: '高' }
const adhocStatusTag = (s) => ({ 1: 'warning', 2: 'primary', 3: 'success' }[s] || 'info')
const priorityTagType = (p) => ({ 1: 'info', 2: 'warning', 3: 'danger' }[p] || 'info')
const adhocLoading = ref(false)
const adhocList = ref([])
const adhocTotal = ref(0)
const adhocQuery = reactive({ pageNo: 1, pageSize: 10, title: '', owner: '', status: null })
const adhocDlg = reactive({ visible: false, title: '' })
const adhocFormRef = ref()
const adhocForm = reactive({ id: null, title: '', owner: '', priority: 2, source: null, dueDate: null, content: '', status: 1 })
const adhocFiles = ref([])
const adhocRules = { title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }] }

async function loadAdhoc() {
  adhocLoading.value = true
  try {
    const res = await taskApi.page(adhocQuery)
    adhocList.value = res?.records || []
    adhocTotal.value = res?.total || 0
  } finally {
    adhocLoading.value = false
  }
}
function resetAdhoc() { Object.assign(adhocQuery, { pageNo: 1, title: '', owner: '', status: null }); loadAdhoc() }
async function doneAdhoc(id) { await taskApi.done(id); ElMessage.success('已完成'); loadAdhoc() }
async function openAdhocDialog(row) {
  adhocDlg.visible = true
  adhocDlg.title = row ? '编辑临时任务' : '新增临时任务'
  if (row) Object.assign(adhocForm, row)
  else Object.assign(adhocForm, { id: null, title: '', owner: '', priority: 2, source: null, dueDate: null, content: '', status: 1 })
  adhocFiles.value = []
  if (row) { try { adhocFiles.value = (await fileApi.list('oa_task', row.id)) || [] } catch { /* ignore */ } }
}
async function submitAdhoc() {
  await adhocFormRef.value.validate()
  let taskId = adhocForm.id
  if (adhocForm.id) await taskApi.update(adhocForm)
  else taskId = await taskApi.add(adhocForm)
  const pendingIds = (adhocFiles.value || []).filter((f) => f && f.id && !f.bizId).map((f) => f.id)
  if (taskId && pendingIds.length) { try { await fileApi.attach('oa_task', taskId, pendingIds) } catch { /* ignore */ } }
  ElMessage.success('保存成功')
  adhocDlg.visible = false
  loadAdhoc()
}
async function removeAdhoc(id) { await taskApi.remove(id); ElMessage.success('删除成功'); loadAdhoc() }

watch(viewMode, (m) => { if (m === 'adhoc' && !adhocList.value.length) loadAdhoc() })
onMounted(loadBoard)
</script>

<style scoped>
.task-page { display: flex; flex-direction: column; gap: 12px; }
.top-header {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px;
  background: #fff; border-radius: 12px; padding: 12px 16px; border: 1px solid #e6e9f2;
}
.header-left, .header-right { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.header-desc { font-size: 12px; color: #64748b; }
.year-btn { font-weight: 700; min-width: 86px; }

.kpi-strip { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; }
.kpi-card {
  background: #fff; border: 1px solid #e6e9f2; border-radius: 12px; padding: 12px 16px;
  display: flex; flex-direction: column; gap: 4px;
}
.kpi-label { font-size: 12px; color: #64748b; }
.kpi-val { font-size: 22px; color: #1e293b; font-variant-numeric: tabular-nums; }
.kpi-val small { font-size: 12px; font-weight: 500; color: #64748b; }
.kpi-done .kpi-val { color: #059669; }
.kpi-cur .kpi-val { color: #4f46e5; }
.kpi-warn { border-color: #fecaca; background: #fef2f2; }
.kpi-warn .kpi-val { color: #dc2626; }

.filter-legend-bar {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px;
  background: #fff; border: 1px solid #e6e9f2; border-radius: 10px; padding: 10px 14px;
}
.filters, .legend { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.leg-item { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: #475569; }
.leg-tip { font-size: 12px; color: #94a3b8; margin-left: 4px; }
.dot {
  display: inline-flex; align-items: center; justify-content: center;
  width: 18px; height: 18px; border-radius: 50%; font-style: normal; font-size: 11px; font-weight: 700;
}
.dot.done { background: #10b981; color: #fff; }
.dot.doing { background: #3b82f6; color: #fff; }
.dot.planned { border: 2px solid #6366f1; color: #6366f1; background: #fff; }
.dot.overdue { background: #ef4444; color: #fff; }

.matrix-card { background: #fff; border: 1px solid #e6e9f2; border-radius: 12px; overflow: hidden; }
.matrix-scroll { overflow-x: auto; }
.matrix-table { width: 100%; border-collapse: separate; border-spacing: 0; min-width: 1120px; }
.matrix-table th, .matrix-table td { border-bottom: 1px solid #eef1f7; border-right: 1px solid #f1f5f9; }
.matrix-table thead th { background: #f8fafc; color: #334155; font-size: 13px; font-weight: 600; padding: 10px 4px; text-align: center; }
.sticky-left { position: sticky; left: 0; z-index: 5; background: #fff; box-shadow: 2px 0 8px rgba(15, 23, 42, 0.04); }
thead .sticky-left { z-index: 8; background: #f8fafc; text-align: left !important; padding-left: 14px !important; }
.task-col { width: 255px; min-width: 245px; }
.month-col { min-width: 68px; cursor: pointer; transition: background 0.15s; }
.month-col:hover { background: #eef2ff !important; }
.month-col.is-current, .node-cell.is-current { background: #f5f7ff; }
.month-col.is-focused { background: #e0e7ff !important; color: #3730a3 !important; }
.node-cell.is-focused { background: #eef2ff; }
.m-head { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.cur-pill { font-size: 10px; background: #4f46e5; color: #fff; padding: 0 6px; border-radius: 999px; line-height: 16px; }
.stat-col { width: 92px; min-width: 90px; }

.task-cell { padding: 10px 10px 10px 12px; vertical-align: middle; }
.task-meta { display: flex; align-items: stretch; gap: 9px; }
.color-bar { width: 4px; border-radius: 4px; flex-shrink: 0; }
.task-text { flex: 1; min-width: 0; }
.task-name-row { display: flex; align-items: center; justify-content: space-between; gap: 4px; }
.task-name { font-size: 13.5px; font-weight: 600; color: #1e293b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row-ops { opacity: 0; transition: opacity 0.15s; display: inline-flex; gap: 2px; flex-shrink: 0; }
.task-row:hover .row-ops { opacity: 1; }
.task-tags { display: flex; align-items: center; gap: 4px; margin-top: 4px; flex-wrap: wrap; }
.cycle-pill { font-size: 11px; padding: 0 6px; border-radius: 999px; border: 1px solid; line-height: 18px; font-weight: 600; }
.sub-chip { font-size: 11px; color: #64748b; background: #f1f5f9; padding: 0 6px; border-radius: 4px; line-height: 18px; }
.task-owner { font-size: 11px; color: #94a3b8; margin-top: 3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.node-cell { position: relative; text-align: center; vertical-align: middle; padding: 10px 4px; height: 74px; }
.track { position: absolute; top: 50%; height: 2px; transform: translateY(-50%); z-index: 1; pointer-events: none; }
.track-left { left: 0; right: 50%; }
.track-right { left: 50%; right: 0; }

.node-btn {
  position: relative; z-index: 3;
  display: inline-flex; flex-direction: column; align-items: center; justify-content: center;
  width: 50px; height: 42px; border-radius: 10px; border: 1.5px solid transparent;
  background: #fff; cursor: pointer; padding: 2px; transition: transform 0.14s, box-shadow 0.14s;
}
.node-btn:hover { transform: scale(1.08); box-shadow: 0 4px 12px rgba(15, 23, 42, 0.12); }
.node-icon { font-size: 13px; font-weight: 700; line-height: 1.1; }
.node-day { font-size: 10px; line-height: 1.1; margin-top: 2px; opacity: 0.9; }
.att-dot { position: absolute; top: -5px; right: -5px; font-size: 11px; }

.node-btn.st-done { background: #10b981; border-color: #059669; color: #fff; }
.node-btn.st-doing { background: #3b82f6; border-color: #2563eb; color: #fff; }
.node-btn.st-overdue { background: #ef4444; border-color: #dc2626; color: #fff; }
.node-btn.st-planned { background: #fff; }

.empty-add-btn {
  position: relative; z-index: 2; width: 26px; height: 26px; border-radius: 50%;
  border: 1px dashed transparent; background: transparent; color: transparent; cursor: pointer; font-size: 14px;
}
.node-cell:hover .empty-add-btn { border-color: #cbd5e1; color: #94a3b8; background: #fff; }

.stat-cell { text-align: center; padding: 8px 10px; }
.row-stat-text { font-size: 12px; font-weight: 600; color: #334155; margin-bottom: 4px; }
.mini-bar { height: 5px; background: #e2e8f0; border-radius: 999px; overflow: hidden; }
.mini-bar-fill { height: 100%; border-radius: 999px; transition: width 0.25s; }
.sum-fill { background: #10b981; }

.summary-row td { background: #f8fafc; padding: 8px 6px; text-align: center; font-size: 12px; }
.summary-title { text-align: left !important; padding-left: 14px !important; font-weight: 600; color: #334155; background: #f8fafc !important; }
.summary-cell { cursor: pointer; }
.summary-cell.is-focused { background: #e0e7ff; }
.sum-num { margin-bottom: 4px; color: #475569; }
.empty-row { text-align: center; padding: 32px; color: #94a3b8; }

.focus-panel { background: #fff; border: 1px solid #e6e9f2; border-radius: 12px; padding: 14px 16px; }
.focus-head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.focus-title { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 700; color: #1e293b; }
.focus-hint { font-size: 12px; color: #94a3b8; }
.focus-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); gap: 12px; }
.focus-card {
  border: 1px solid #e6e9f2; border-radius: 10px; padding: 12px 14px; background: #fafbfe;
  display: flex; flex-direction: column; gap: 6px;
}
.fc-top { display: flex; align-items: center; gap: 8px; }
.fc-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.fc-name { flex: 1; font-size: 14px; color: #1e293b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fc-meta { display: flex; flex-wrap: wrap; gap: 8px; font-size: 12px; color: #64748b; }
.fc-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8px; margin-top: 2px; }
.focus-empty { text-align: center; color: #94a3b8; padding: 18px 0; font-size: 13px; }

.months-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px 12px; width: 100%; }
.color-picks { display: flex; align-items: center; gap: 8px; }
.cp-dot { width: 22px; height: 22px; border-radius: 50%; cursor: pointer; border: 2px solid transparent; }
.cp-dot.active { border-color: #1e293b; transform: scale(1.12); }
.tip-box { font-size: 12px; line-height: 1.6; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
