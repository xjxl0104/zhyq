<template>
  <div class="page-container routine-page">
    <!-- 顶部主视图切换 -->
    <div class="top-bar">
      <div class="top-left">
        <h3 class="mod-title">{{ moduleMeta.title }}</h3>
        <el-radio-group v-model="activeTab" size="default">
          <el-radio-button value="tasks">每日巡检任务（24h）</el-radio-button>
          <el-radio-button value="analytics">可视化汇总与分析</el-radio-button>
          <el-radio-button value="templates">固定模板与频率</el-radio-button>
          <el-radio-button value="inspectors">巡检人管理</el-radio-button>
        </el-radio-group>
      </div>
      <div class="top-right">
        <span class="rule-badge">强制现场即时拍照 · 自动水印 · 全项拍完自动完成</span>
      </div>
    </div>

    <!-- ==================== Tab 1：每日巡检任务（24小时周期，未完成标红） ==================== -->
    <template v-if="activeTab === 'tasks'">
      <div class="filter-bar">
        <div class="f-left">
          <el-button-group size="small">
            <el-button @click="shiftDate(-1)">前一天</el-button>
            <el-button @click="taskDate = todayStr">今天</el-button>
            <el-button @click="shiftDate(1)">后一天</el-button>
          </el-button-group>
          <el-date-picker v-model="taskDate" type="date" size="small" value-format="YYYY-MM-DD" :clearable="false" style="width: 140px" />
          <el-select v-model="filterStatus" placeholder="全部状态" clearable size="small" style="width: 145px">
            <el-option label="全部未完成（标红）" value="INCOMPLETE" />
            <el-option label="待巡检" value="PENDING" />
            <el-option label="巡检中" value="IN_PROGRESS" />
            <el-option label="已逾期未完成" value="OVERDUE" />
            <el-option label="已自动完成" value="COMPLETED" />
          </el-select>
          <el-select v-model="filterInspectorId" placeholder="全部巡检人" clearable size="small" style="width: 145px">
            <el-option v-for="i in inspectors" :key="i.id" :label="`${i.name}（${i.position || '巡检员'}）`" :value="i.id" />
          </el-select>
        </div>
        <div class="f-right">
          <el-button size="small" @click="loadTasks">刷新当日任务</el-button>
          <el-button size="small" type="primary" plain @click="activeTab = 'templates'">配置巡检模板</el-button>
        </div>
      </div>

      <!-- 当日速览 KPI -->
      <div class="day-kpi">
        <div class="dk-card"><span>{{ taskDate }} 应检班次</span><strong>{{ taskList.length }}</strong></div>
        <div class="dk-card ok"><span>已自动完成</span><strong>{{ doneCount }}</strong></div>
        <div class="dk-card" :class="{ red: incompleteCount > 0 }"><span>未完成（标红提醒）</span><strong>{{ incompleteCount }}</strong></div>
        <div class="dk-card" :class="{ red: overdueCount > 0 }"><span>已逾期漏检</span><strong>{{ overdueCount }}</strong></div>
        <div class="dk-card" :class="{ warn: totalIssueCount > 0 }"><span>发现异常项</span><strong>{{ totalIssueCount }}</strong></div>
      </div>

      <!-- 巡检任务表格（未完成整行淡红底 + 红色标签；禁止手动标完成！） -->
      <div class="table-card" v-loading="tasksLoading">
        <el-table :data="taskList" border :row-class-name="taskRowClass">
          <el-table-column label="班次 / 24h窗口" width="165">
            <template #default="{ row }">
              <div class="slot-main">{{ row.task.slotLabel }}</div>
              <div class="slot-sub">截止 {{ shortTime(row.task.deadlineTime) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="巡检任务 / 区域" min-width="220">
            <template #default="{ row }">
              <div class="t-name">{{ row.task.templateName }}</div>
              <div class="t-area">{{ row.task.area || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="巡检人" width="165">
            <template #default="{ row }">
              <div><strong>{{ row.task.inspectorName || '未指派' }}</strong> <span class="muted">{{ row.task.inspectorPosition }}</span></div>
              <div class="muted">{{ row.task.inspectorPhone }}</div>
            </template>
          </el-table-column>
          <el-table-column label="拍照进度（自动判定）" width="190">
            <template #default="{ row }">
              <el-progress
                :percentage="progressPct(row.task)"
                :status="row.task.status === 'COMPLETED' ? 'success' : 'exception'"
                :stroke-width="10"
              />
              <div class="prog-txt">
                已拍 {{ row.task.completedItems || 0 }} / {{ row.task.totalItems || 0 }} 项
                <span v-if="row.task.issueCount > 0" class="issue-pill">异常 {{ row.task.issueCount }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="128" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.task.status === 'COMPLETED'" type="success" effect="dark">已自动完成</el-tag>
              <el-tag v-else-if="row.task.status === 'OVERDUE'" type="danger" effect="dark">已逾期未完成</el-tag>
              <el-tag v-else-if="row.task.status === 'IN_PROGRESS'" type="danger" effect="plain">巡检中（未完）</el-tag>
              <el-tag v-else type="danger" effect="plain">待巡检（未完）</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="自动完成时间" width="155">
            <template #default="{ row }">{{ row.task.completedTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="145" fixed="right" align="center">
            <template #default="{ row }">
              <el-button
                :type="row.task.status === 'COMPLETED' ? 'success' : 'danger'"
                :plain="row.task.status === 'COMPLETED'"
                size="small"
                @click="openTaskDrawer(row)"
              >
                {{ row.task.status === 'COMPLETED' ? '查看水印记录' : '去拍照打卡' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="!tasksLoading && !taskList.length" class="empty-box">
          {{ taskDate }} 暂无排班巡检任务；请到「固定模板与频率」启用或新增巡检模板。
        </div>
      </div>
    </template>

    <!-- ==================== Tab 2：可视化汇总与分析 ==================== -->
    <RoutineAnalyticsPanel v-else-if="activeTab === 'analytics'" ref="analyticsRef" :module-type="moduleType" />

    <!-- ==================== Tab 3：固定巡检模板与24h频率 ==================== -->
    <template v-else-if="activeTab === 'templates'">
      <div class="table-card" v-loading="templatesLoading">
        <div class="toolbar">
          <el-button type="primary" @click="openTemplateDialog(null)"><el-icon><Plus /></el-icon>新增巡检模板</el-button>
          <span class="tip">每个模板可预设固定检查项（随时可改）、指定巡检人、配置 24 小时周期内的巡检频率；启用后每天自动生成巡检任务。</span>
        </div>
        <el-table :data="templateList" border stripe>
          <el-table-column label="模板名称 / 默认区域" min-width="210">
            <template #default="{ row }">
              <div class="t-name">{{ row.template.name }}</div>
              <div class="t-area">{{ row.template.area || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="巡检人" width="170">
            <template #default="{ row }">
              <div><strong>{{ row.template.inspectorName || '未指派' }}</strong> <span class="muted">{{ row.template.inspectorPosition }}</span></div>
              <div class="muted">{{ row.template.inspectorPhone }}</div>
            </template>
          </el-table-column>
          <el-table-column label="24小时巡检频率" min-width="210">
            <template #default="{ row }">
              <el-tag size="small" type="primary" effect="plain">{{ freqSummary(row.template) }}</el-tag>
              <div class="muted" style="margin-top: 3px">{{ weekdaysText(row.template.weekdays) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="预设检查项" width="100" align="center">
            <template #default="{ row }">{{ (row.items || []).length }} 项</template>
          </el-table-column>
          <el-table-column label="状态" width="85" align="center">
            <template #default="{ row }">
              <el-tag :type="row.template.status === 1 ? 'success' : 'info'" size="small">
                {{ row.template.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="145" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTemplateDialog(row)">编辑模板</el-button>
              <el-popconfirm title="确认删除该巡检模板？" @confirm="removeTemplate(row.template.id)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- ==================== Tab 4：巡检人管理（四模块共用） ==================== -->
    <template v-else>
      <div class="table-card" v-loading="inspectorsLoading">
        <div class="toolbar">
          <el-button type="primary" @click="openInspectorDialog(null)"><el-icon><Plus /></el-icon>新建巡检人</el-button>
          <span class="tip">巡检人库在设备巡检、安防巡更、保洁检查、绿化检查四个模块间共用，配置后可在模板中直接选用并自动带入拍照水印。</span>
        </div>
        <el-table :data="allInspectors" border stripe>
          <el-table-column prop="name" label="姓名" width="130" />
          <el-table-column prop="phone" label="联系电话" width="145" />
          <el-table-column prop="position" label="职位 / 岗位" width="150" />
          <el-table-column label="负责条线" min-width="200">
            <template #default="{ row }">{{ scopeLabel(row.moduleScope) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="85" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '在岗' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
          <el-table-column label="操作" width="130" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openInspectorDialog(row)">编辑</el-button>
              <el-popconfirm title="确认删除该巡检人？" @confirm="removeInspector(row.id)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- ==================== 任务执行抽屉（逐项即时拍照打卡） ==================== -->
    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="780px" destroy-on-close>
      <template v-if="currentTask">
        <div class="drawer-head-card" :class="{ done: currentTask.task.status === 'COMPLETED', red: currentTask.task.status !== 'COMPLETED' }">
          <div>
            <div class="dh-title">{{ currentTask.task.templateName }} · {{ currentTask.task.slotLabel }}</div>
            <div class="dh-meta">
              巡检人：<strong>{{ currentTask.task.inspectorName || '未指派' }}</strong>（{{ currentTask.task.inspectorPosition || '-' }} / {{ currentTask.task.inspectorPhone || '-' }}）
              · 区域：{{ currentTask.task.area || '-' }}
            </div>
            <div class="dh-meta">截止时间：{{ currentTask.task.deadlineTime }} · 进度：{{ currentTask.task.completedItems }} / {{ currentTask.task.totalItems }} 项</div>
          </div>
          <el-tag :type="currentTask.task.status === 'COMPLETED' ? 'success' : 'danger'" effect="dark" size="large">
            {{ currentTask.task.status === 'COMPLETED' ? '已自动完成' : (currentTask.task.status === 'OVERDUE' ? '已逾期未完成' : '未完成（需全项拍照）') }}
          </el-tag>
        </div>

        <el-alert
          v-if="currentTask.task.status !== 'COMPLETED'"
          type="error"
          show-icon
          :closable="false"
          style="margin-bottom: 12px"
          title="系统已禁用手动勾完成：请逐项完成现场即时拍照上传，全部预设检查项拍完后本任务自动变为「已完成」。"
        />

        <div class="check-item-list">
          <div
            v-for="item in currentTask.items"
            :key="item.id"
            class="ci-card"
            :class="{ checked: item.checked === 1, abnormal: item.checked === 1 && item.result === 'ABNORMAL', unchecked: item.checked !== 1 }"
          >
            <div class="ci-top">
              <div class="ci-title">
                <span class="seq-badge">{{ item.seqNo }}</span>
                <strong>{{ item.itemName }}</strong>
                <el-tag size="small" effect="plain">{{ item.areaPoint || currentTask.task.area }}</el-tag>
              </div>
              <div class="ci-status">
                <el-tag v-if="item.checked !== 1" type="danger" size="small" effect="dark">未拍照（标红）</el-tag>
                <el-tag v-else-if="item.result === 'ABNORMAL'" type="danger" size="small" effect="plain">已拍照 · 发现异常</el-tag>
                <el-tag v-else type="success" size="small" effect="plain">已拍照 · 正常</el-tag>
              </div>
            </div>
            <div v-if="item.standard" class="ci-std">检查标准：{{ item.standard }}</div>

            <!-- 已上传水印照片缩略图 -->
            <div v-if="parsePhotos(item).length" class="photo-row">
              <div v-for="(p, pi) in parsePhotos(item)" :key="pi" class="photo-thumb" @click="previewPhoto(p.fileId)">
                <img v-if="photoBlobUrls[p.fileId]" :src="photoBlobUrls[p.fileId]" alt="水印照片" />
                <span v-else class="loading-img">加载中...</span>
              </div>
              <div class="photo-meta">
                <div>打卡人：{{ item.capturedBy || '-' }}</div>
                <div>时间：{{ item.capturedTime || '-' }}</div>
                <div v-if="item.result === 'ABNORMAL'" class="abn-txt">
                  [{{ item.issueCategory }}] {{ item.issueDesc }}
                </div>
              </div>
            </div>

            <div class="ci-actions">
              <el-button
                :type="item.checked === 1 ? 'primary' : 'danger'"
                :plain="item.checked === 1"
                size="small"
                @click="openCaptureDialog(item)"
              >
                <el-icon><Camera /></el-icon>{{ item.checked === 1 ? '补拍 / 重拍水印照' : '立即现场拍照打卡' }}
              </el-button>
              <template v-if="item.checked === 1 && item.result === 'ABNORMAL'">
                <el-tag v-if="item.workOrderCode" type="success" size="small">已转报修工单：{{ item.workOrderCode }}</el-tag>
                <el-button v-else type="warning" size="small" plain @click="convertItemToOrder(item)">
                  一键转物业报修工单
                </el-button>
              </template>
            </div>
          </div>
        </div>
      </template>
    </el-drawer>

    <!-- ==================== 现场即时拍照 + 自动水印弹窗 ==================== -->
    <el-dialog v-model="captureDlg.visible" title="现场即时拍照打卡（自动加水印 · 禁止相册传图）" width="700px" destroy-on-close append-to-body>
      <template v-if="captureDlg.item && currentTask">
        <div class="cap-info">
          <div><strong>检查项：</strong>{{ captureDlg.item.itemName }}</div>
          <div><strong>检查区域：</strong>{{ captureDlg.item.areaPoint || currentTask.task.area }}</div>
          <div v-if="captureDlg.item.standard"><strong>检查标准：</strong>{{ captureDlg.item.standard }}</div>
        </div>

        <el-form :model="captureDlg" label-width="86px" size="small" style="margin-bottom: 10px">
          <el-row :gutter="10">
            <el-col :span="12">
              <el-form-item label="检查结论">
                <el-radio-group v-model="captureDlg.result">
                  <el-radio value="NORMAL">正常</el-radio>
                  <el-radio value="ABNORMAL">发现异常</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="现场巡检人">
                <el-input v-model="captureDlg.capturedBy" placeholder="自动带入模板巡检人" />
              </el-form-item>
            </el-col>
            <template v-if="captureDlg.result === 'ABNORMAL'">
              <el-col :span="10">
                <el-form-item label="异常分类" required>
                  <el-select v-model="captureDlg.issueCategory" style="width: 100%">
                    <el-option v-for="c in issueCategories" :key="c" :label="c" :value="c" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="14">
                <el-form-item label="问题描述" required>
                  <el-input v-model="captureDlg.issueDesc" placeholder="请简述现场发现的问题" />
                </el-form-item>
              </el-col>
            </template>
          </el-row>
        </el-form>

        <LiveWatermarkCamera
          :inspector-name="captureDlg.capturedBy || currentTask.task.inspectorName"
          :inspector-position="currentTask.task.inspectorPosition"
          :inspector-phone="currentTask.task.inspectorPhone"
          :area="captureDlg.item.areaPoint || currentTask.task.area"
          :task-title="`${moduleMeta.title} · ${currentTask.task.templateName}`"
          :item-name="captureDlg.item.itemName"
          :result-label="captureDlg.result === 'ABNORMAL' ? '发现异常' : '正常'"
          @uploaded="onPhotoUploaded"
        />

        <div v-if="captureDlg.photos.length" class="cap-uploaded-list">
          <div class="sub">本次已拍水印照片（{{ captureDlg.photos.length }} / 最少 {{ captureDlg.item.minPhotos || 1 }} 张）：</div>
          <div class="photo-row">
            <div v-for="(p, idx) in captureDlg.photos" :key="p.fileId" class="photo-thumb">
              <img v-if="photoBlobUrls[p.fileId]" :src="photoBlobUrls[p.fileId]" alt="已拍" @click="previewPhoto(p.fileId)" />
              <el-button class="del-p" circle size="small" type="danger" @click="captureDlg.photos.splice(idx, 1)">×</el-button>
            </div>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="captureDlg.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="captureDlg.submitting"
          :disabled="!captureDlg.photos.length"
          @click="submitCapture"
        >
          提交打卡（{{ captureDlg.photos.length }} 张水印照）
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 巡检模板新增/编辑弹窗 ==================== -->
    <el-dialog v-model="tplDlg.visible" :title="tplDlg.form.id ? '编辑巡检模板' : '新增巡检模板'" width="880px" top="4vh" destroy-on-close>
      <el-form :model="tplDlg.form" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="模板名称" required>
              <el-input v-model="tplDlg.form.name" placeholder="如：配电房与消防水泵每日巡检" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认巡检区域">
              <el-input v-model="tplDlg.form.area" placeholder="如：配电房 / 1-8层公共区" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="指定巡检人" required>
              <div class="ins-pick-row">
                <el-select v-model="tplDlg.form.inspectorId" filterable clearable placeholder="请选择巡检人" style="flex: 1">
                  <el-option v-for="i in allInspectors" :key="i.id" :label="`${i.name} · ${i.position || '巡检员'} · ${i.phone || '无电话'}`" :value="i.id" />
                </el-select>
                <el-button type="primary" plain @click="openInspectorDialog(null, true)">+ 新建巡检人</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-radio-group v-model="tplDlg.form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>

          <!-- 24小时周期巡检频率配置 -->
          <el-col :span="24">
            <el-form-item label="24小时频率模式">
              <el-radio-group v-model="tplDlg.form.freqType">
                <el-radio value="DAILY_SLOTS">每日固定时段（如 09:00, 15:00, 21:00）</el-radio>
                <el-radio value="INTERVAL_HOURS">24小时内每隔 N 小时巡检一次</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>

          <template v-if="tplDlg.form.freqType === 'DAILY_SLOTS'">
            <el-col :span="24">
              <el-form-item label="每日打卡时段">
                <el-input v-model="tplDlg.form.timeSlots" placeholder="英文逗号分隔，例如 09:00,15:00,21:00（每个时段自动生成一次任务）" />
              </el-form-item>
            </el-col>
          </template>
          <template v-else>
            <el-col :span="8">
              <el-form-item label="每隔几小时">
                <el-select v-model="tplDlg.form.intervalHours" style="width: 100%">
                  <el-option v-for="h in [1, 2, 3, 4, 6, 8, 12, 24]" :key="h" :label="`每 ${h} 小时一次`" :value="h" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="开始时间">
                <el-input v-model="tplDlg.form.startTime" placeholder="00:00" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="截止时间">
                <el-input v-model="tplDlg.form.endTime" placeholder="24:00" />
              </el-form-item>
            </el-col>
          </template>

          <el-col :span="24">
            <el-form-item label="执行星期">
              <el-checkbox-group v-model="tplDlg.weekdayArr">
                <el-checkbox v-for="w in weekdayOptions" :key="w.val" :value="w.val">{{ w.label }}</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="items-editor-head">
          <strong>预设检查项清单（全部项现场即时拍照完成后自动结束该次巡检）</strong>
          <el-button size="small" type="primary" plain @click="addTplItem">+ 添加检查项</el-button>
        </div>
        <el-table :data="tplDlg.items" border size="small" max-height="260">
          <el-table-column type="index" label="#" width="44" align="center" />
          <el-table-column label="检查项内容" min-width="200">
            <template #default="{ row }"><el-input v-model="row.itemName" size="small" placeholder="如：配电柜电压及温升检查" /></template>
          </el-table-column>
          <el-table-column label="具体区域/点位" width="150">
            <template #default="{ row }"><el-input v-model="row.areaPoint" size="small" placeholder="留空沿用默认区域" /></template>
          </el-table-column>
          <el-table-column label="检查合格标准" min-width="190">
            <template #default="{ row }"><el-input v-model="row.standard" size="small" placeholder="合格判定标准" /></template>
          </el-table-column>
          <el-table-column label="最少拍照" width="84" align="center">
            <template #default="{ row }"><el-input-number v-model="row.minPhotos" :min="1" :max="5" :controls="false" size="small" style="width: 56px" /></template>
          </el-table-column>
          <el-table-column label="删" width="52" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="tplDlg.items.splice($index, 1)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="tplDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="tplDlg.saving" @click="saveTemplate">保存模板并同步今日任务</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 新建/编辑巡检人弹窗 ==================== -->
    <el-dialog v-model="insDlg.visible" :title="insDlg.form.id ? '编辑巡检人' : '新建巡检人'" width="480px" append-to-body destroy-on-close>
      <el-form :model="insDlg.form" label-width="88px" size="small">
        <el-form-item label="姓名" required><el-input v-model="insDlg.form.name" placeholder="请输入姓名" /></el-form-item>
        <el-form-item label="联系电话" required><el-input v-model="insDlg.form.phone" placeholder="请输入手机号码" /></el-form-item>
        <el-form-item label="职位/岗位" required><el-input v-model="insDlg.form.position" placeholder="如：工程巡检员 / 保安队长 / 保洁主管" /></el-form-item>
        <el-form-item label="负责条线">
          <el-checkbox-group v-model="insDlg.scopes">
            <el-checkbox value="EQUIPMENT">设备巡检</el-checkbox>
            <el-checkbox value="PATROL">安防巡更</el-checkbox>
            <el-checkbox value="CLEANING">保洁检查</el-checkbox>
            <el-checkbox value="GREENING">绿化检查</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="insDlg.form.status">
            <el-radio :value="1">在岗</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="insDlg.form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="insDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="insDlg.saving" @click="saveInspector">保存</el-button>
      </template>
    </el-dialog>

    <!-- 水印照片高清大图预览 -->
    <el-dialog v-model="previewDlg.visible" title="现场水印照片查验" width="860px" append-to-body>
      <div class="big-preview">
        <img v-if="previewDlg.url" :src="previewDlg.url" alt="现场水印照片" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, Plus } from '@element-plus/icons-vue'
import { routineInspectionApi } from '@/api/property'
import { fileApi } from '@/api/file'
import LiveWatermarkCamera from './LiveWatermarkCamera.vue'
import RoutineAnalyticsPanel from './RoutineAnalyticsPanel.vue'

const props = defineProps({
  /** EQUIPMENT | PATROL | CLEANING | GREENING */
  moduleType: { type: String, required: true }
})

const MODULE_META = {
  EQUIPMENT: { title: '设备巡检', categories: ['设备故障', '仪表异常', '异响温升', '渗漏风险', '安全隐患', '其他异常'] },
  PATROL: { title: '安防巡更', categories: ['消防通道占用', '门禁/道闸异常', '监控/照明故障', '周界安全隐患', '人员/车辆违规', '其他异常'] },
  CLEANING: { title: '保洁检查', categories: ['地面脏污/积水', '卫生间异味/缺耗材', '垃圾满溢未清', '墙面/设施积灰', '消杀不到位', '其他异常'] },
  GREENING: { title: '绿化检查', categories: ['缺水枯萎', '杂草/垃圾未清', '绿篱未修剪', '病虫害迹象', '苗木倒伏损坏', '其他异常'] }
}
const moduleMeta = computed(() => MODULE_META[props.moduleType] || MODULE_META.EQUIPMENT)
const issueCategories = computed(() => moduleMeta.value.categories)

const weekdayOptions = [
  { val: '1', label: '周一' }, { val: '2', label: '周二' }, { val: '3', label: '周三' },
  { val: '4', label: '周四' }, { val: '5', label: '周五' }, { val: '6', label: '周六' }, { val: '7', label: '周日' }
]

const todayStr = new Date().toISOString().slice(0, 10)
const activeTab = ref('tasks')
const taskDate = ref(todayStr)
const filterStatus = ref('')
const filterInspectorId = ref(null)

const tasksLoading = ref(false)
const templatesLoading = ref(false)
const inspectorsLoading = ref(false)
const taskList = ref([])
const templateList = ref([])
const inspectors = ref([])
const allInspectors = ref([])
const analyticsRef = ref(null)

const doneCount = computed(() => taskList.value.filter((x) => x.task.status === 'COMPLETED').length)
const overdueCount = computed(() => taskList.value.filter((x) => x.task.status === 'OVERDUE').length)
const incompleteCount = computed(() => taskList.value.filter((x) => x.task.status !== 'COMPLETED').length)
const totalIssueCount = computed(() => taskList.value.reduce((s, x) => s + Number(x.task.issueCount || 0), 0))

function shiftDate(days) {
  const d = new Date(`${taskDate.value}T00:00:00`)
  d.setDate(d.getDate() + days)
  const pad = (n) => String(n).padStart(2, '0')
  taskDate.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function progressPct(t) {
  if (!t?.totalItems) return 0
  return Math.min(100, Math.round(((t.completedItems || 0) / t.totalItems) * 100))
}

function shortTime(dt) {
  if (!dt) return '-'
  return String(dt).slice(5, 16)
}

function taskRowClass({ row }) {
  return row.task.status !== 'COMPLETED' ? 'row-incomplete' : ''
}

function freqSummary(tpl) {
  if (tpl.freqType === 'INTERVAL_HOURS') {
    return `每 ${tpl.intervalHours || 4} 小时（${tpl.startTime || '00:00'}~${tpl.endTime || '24:00'}）`
  }
  return `固定时段：${tpl.timeSlots || '09:00'}`
}

function weekdaysText(w) {
  if (!w || w === '1,2,3,4,5,6,7') return '每天执行'
  if (w === '1,2,3,4,5') return '周一至周五'
  const map = { 1: '一', 2: '二', 3: '三', 4: '四', 5: '五', 6: '六', 7: '日' }
  return '每周' + w.split(',').map((k) => map[k.trim()] || k).join('、')
}

function scopeLabel(scope) {
  if (!scope || scope === 'ALL') return '全部四条线'
  const map = { EQUIPMENT: '设备巡检', PATROL: '安防巡更', CLEANING: '保洁检查', GREENING: '绿化检查' }
  return scope.split(',').map((s) => map[s.trim()] || s).join(' / ')
}

// ---- 鉴权照片 Blob 缓存（/api/file/download/{id} 需携带 JWT） ----
const photoBlobUrls = reactive({})
async function ensurePhotoBlob(fileId) {
  if (!fileId || photoBlobUrls[fileId]) return
  try {
    const res = await fileApi.download(fileId)
    const blob = res?.data instanceof Blob ? res.data : res
    if (blob instanceof Blob) {
      photoBlobUrls[fileId] = URL.createObjectURL(blob)
    }
  } catch {}
}

function parsePhotos(item) {
  if (!item?.photoUrlsJson) return []
  try {
    const arr = JSON.parse(item.photoUrlsJson)
    if (Array.isArray(arr)) {
      arr.forEach((p) => { if (p?.fileId) ensurePhotoBlob(p.fileId) })
      return arr
    }
  } catch {}
  return []
}

const previewDlg = reactive({ visible: false, url: '' })
async function previewPhoto(fileId) {
  await ensurePhotoBlob(fileId)
  previewDlg.url = photoBlobUrls[fileId] || ''
  previewDlg.visible = true
}

// ---- 数据加载 ----
async function loadInspectors() {
  inspectorsLoading.value = true
  try {
    const [scoped, all] = await Promise.all([
      routineInspectionApi.inspectors({ moduleType: props.moduleType, onlyActive: true }),
      routineInspectionApi.inspectors({})
    ])
    inspectors.value = scoped || []
    allInspectors.value = all || []
  } finally {
    inspectorsLoading.value = false
  }
}

async function loadTasks() {
  tasksLoading.value = true
  try {
    taskList.value = await routineInspectionApi.tasks({
      moduleType: props.moduleType,
      date: taskDate.value,
      status: filterStatus.value || undefined,
      inspectorId: filterInspectorId.value || undefined
    }) || []
  } finally {
    tasksLoading.value = false
  }
}

async function loadTemplates() {
  templatesLoading.value = true
  try {
    templateList.value = await routineInspectionApi.templates(props.moduleType) || []
  } finally {
    templatesLoading.value = false
  }
}

// ---- 任务打卡抽屉 ----
const drawerVisible = ref(false)
const currentTask = ref(null)
const drawerTitle = computed(() => currentTask.value ? `${moduleMeta.value.title} · 现场拍照打卡` : '现场拍照打卡')

async function openTaskDrawer(row) {
  currentTask.value = await routineInspectionApi.getTask(row.task.id)
  ;(currentTask.value.items || []).forEach((it) => parsePhotos(it))
  drawerVisible.value = true
}

const captureDlg = reactive({
  visible: false,
  item: null,
  result: 'NORMAL',
  issueCategory: '',
  issueDesc: '',
  capturedBy: '',
  photos: [],
  submitting: false
})

function openCaptureDialog(item) {
  captureDlg.item = item
  captureDlg.result = item.result === 'ABNORMAL' ? 'ABNORMAL' : 'NORMAL'
  captureDlg.issueCategory = item.issueCategory || issueCategories.value[0]
  captureDlg.issueDesc = item.issueDesc || ''
  captureDlg.capturedBy = item.capturedBy || currentTask.value?.task?.inspectorName || ''
  captureDlg.photos = parsePhotos(item).map((p) => ({ ...p }))
  captureDlg.visible = true
}

function onPhotoUploaded(proof) {
  captureDlg.photos.push(proof)
  ensurePhotoBlob(proof.fileId)
}

async function submitCapture() {
  if (!captureDlg.photos.length) return ElMessage.warning('请先现场即时拍照')
  if (captureDlg.result === 'ABNORMAL' && !captureDlg.issueDesc.trim()) {
    return ElMessage.warning('发现异常时请填写问题描述')
  }
  captureDlg.submitting = true
  try {
    const res = await routineInspectionApi.captureItem(currentTask.value.task.id, captureDlg.item.id, {
      photos: captureDlg.photos,
      result: captureDlg.result,
      issueCategory: captureDlg.result === 'ABNORMAL' ? captureDlg.issueCategory : '',
      issueDesc: captureDlg.result === 'ABNORMAL' ? captureDlg.issueDesc.trim() : '',
      capturedBy: captureDlg.capturedBy
    })
    currentTask.value = { task: res.task, items: res.items }
    ;(res.items || []).forEach((it) => parsePhotos(it))
    captureDlg.visible = false
    if (res.autoCompleted) {
      ElMessage.success('全部预设检查项均已完成现场拍照，系统已自动完成本次巡检任务！')
    } else {
      ElMessage.success('本项现场水印拍照打卡已保存')
    }
    await loadTasks()
  } finally {
    captureDlg.submitting = false
  }
}

async function convertItemToOrder(item) {
  const res = await routineInspectionApi.toWorkOrder(item.id)
  ElMessage.success(`已一键生成物业报修工单：${res.workOrderCode}`)
  currentTask.value = await routineInspectionApi.getTask(currentTask.value.task.id)
}

// ---- 模板编辑 ----
const tplDlg = reactive({
  visible: false,
  saving: false,
  weekdayArr: ['1', '2', '3', '4', '5', '6', '7'],
  form: {},
  items: []
})

function openTemplateDialog(row) {
  if (row) {
    tplDlg.form = { ...row.template }
    tplDlg.weekdayArr = (row.template.weekdays || '1,2,3,4,5,6,7').split(',').map((s) => s.trim()).filter(Boolean)
    tplDlg.items = (row.items || []).map((i) => ({ ...i }))
  } else {
    tplDlg.form = {
      id: null,
      moduleType: props.moduleType,
      name: '',
      area: '',
      inspectorId: inspectors.value[0]?.id || null,
      freqType: 'DAILY_SLOTS',
      intervalHours: 4,
      startTime: '08:00',
      endTime: '20:00',
      timeSlots: '09:00,15:00',
      status: 1,
      sortOrder: 10,
      remark: ''
    }
    tplDlg.weekdayArr = ['1', '2', '3', '4', '5', '6', '7']
    tplDlg.items = [{ itemName: '', areaPoint: '', standard: '', minPhotos: 1 }]
  }
  tplDlg.visible = true
}

function addTplItem() {
  tplDlg.items.push({ itemName: '', areaPoint: '', standard: '', minPhotos: 1 })
}

async function saveTemplate() {
  if (!tplDlg.form.name?.trim()) return ElMessage.warning('请填写模板名称')
  const validItems = tplDlg.items.filter((i) => i.itemName?.trim())
  if (!validItems.length) return ElMessage.warning('请至少填写一条预设检查项')
  tplDlg.saving = true
  try {
    await routineInspectionApi.saveTemplate({
      ...tplDlg.form,
      moduleType: props.moduleType,
      weekdays: tplDlg.weekdayArr.length ? tplDlg.weekdayArr.join(',') : '1,2,3,4,5,6,7',
      items: validItems
    })
    ElMessage.success('巡检模板已保存，并已同步生成今日排班任务')
    tplDlg.visible = false
    await Promise.all([loadTemplates(), loadTasks()])
  } finally {
    tplDlg.saving = false
  }
}

async function removeTemplate(id) {
  await routineInspectionApi.deleteTemplate(id)
  ElMessage.success('已删除模板')
  await loadTemplates()
}

// ---- 巡检人管理 ----
const insDlg = reactive({
  visible: false,
  saving: false,
  bindAfterSave: false,
  scopes: ['EQUIPMENT', 'PATROL', 'CLEANING', 'GREENING'],
  form: {}
})

function openInspectorDialog(row, bindAfterSave = false) {
  insDlg.bindAfterSave = bindAfterSave
  if (row) {
    insDlg.form = { ...row }
    insDlg.scopes = !row.moduleScope || row.moduleScope === 'ALL'
      ? ['EQUIPMENT', 'PATROL', 'CLEANING', 'GREENING']
      : row.moduleScope.split(',').map((s) => s.trim()).filter(Boolean)
  } else {
    insDlg.form = { id: null, name: '', phone: '', position: '', status: 1, remark: '' }
    insDlg.scopes = [props.moduleType]
  }
  insDlg.visible = true
}

async function saveInspector() {
  if (!insDlg.form.name?.trim()) return ElMessage.warning('请填写巡检人姓名')
  insDlg.saving = true
  try {
    const scopeStr = insDlg.scopes.length >= 4 || !insDlg.scopes.length ? 'ALL' : insDlg.scopes.join(',')
    const saved = await routineInspectionApi.saveInspector({ ...insDlg.form, moduleScope: scopeStr })
    ElMessage.success('巡检人已保存')
    insDlg.visible = false
    await loadInspectors()
    if (insDlg.bindAfterSave && saved?.id) {
      tplDlg.form.inspectorId = saved.id
    }
  } finally {
    insDlg.saving = false
  }
}

async function removeInspector(id) {
  await routineInspectionApi.deleteInspector(id)
  ElMessage.success('已删除巡检人')
  await loadInspectors()
}

// ---- 监听切换 ----
watch([taskDate, filterStatus, filterInspectorId], () => {
  if (activeTab.value === 'tasks') loadTasks()
})
watch(activeTab, (tab) => {
  if (tab === 'tasks') loadTasks()
  else if (tab === 'templates') loadTemplates()
  else if (tab === 'inspectors') loadInspectors()
  else if (tab === 'analytics') analyticsRef.value?.refresh?.()
})
watch(() => props.moduleType, async () => {
  activeTab.value = 'tasks'
  await Promise.all([loadInspectors(), loadTemplates(), loadTasks()])
}, { immediate: true })

onBeforeUnmount(() => {
  Object.values(photoBlobUrls).forEach((u) => { try { URL.revokeObjectURL(u) } catch {} })
})
</script>

<style scoped>
.routine-page { display: flex; flex-direction: column; gap: 12px; }
.top-bar {
  display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;
  padding: 12px 16px; border-radius: 10px; background: var(--el-fill-color-blank); border: 1px solid var(--el-border-color-lighter);
}
.top-left { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.mod-title { margin: 0; font-size: 16px; font-weight: 700; }
.rule-badge {
  font-size: 12px; padding: 4px 10px; border-radius: 999px;
  background: var(--el-color-danger-light-9); color: var(--el-color-danger); border: 1px solid var(--el-color-danger-light-5);
}
.filter-bar {
  display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap;
  padding: 10px 14px; border-radius: 10px; background: var(--el-fill-color-blank); border: 1px solid var(--el-border-color-lighter);
}
.f-left, .f-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.day-kpi { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; }
.dk-card {
  padding: 10px 14px; border-radius: 10px; background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter); display: flex; flex-direction: column; gap: 3px;
}
.dk-card span { font-size: 12px; color: var(--el-text-color-secondary); }
.dk-card strong { font-size: 22px; font-variant-numeric: tabular-nums; }
.dk-card.ok strong { color: #16a34a; }
.dk-card.red { border-color: var(--el-color-danger-light-5); background: var(--el-color-danger-light-9); }
.dk-card.red strong { color: #dc2626; }
.dk-card.warn strong { color: #d97706; }
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.tip { font-size: 12px; color: var(--el-text-color-secondary); }
.slot-main { font-weight: 700; font-size: 13px; }
.slot-sub { font-size: 11px; color: var(--el-text-color-secondary); }
.t-name { font-weight: 600; }
.t-area { font-size: 12px; color: var(--el-text-color-secondary); }
.muted { font-size: 12px; color: var(--el-text-color-secondary); }
.prog-txt { font-size: 11px; color: var(--el-text-color-secondary); margin-top: 2px; display: flex; align-items: center; gap: 6px; }
.issue-pill { color: #dc2626; font-weight: 700; }
.empty-box { text-align: center; padding: 28px 0; color: var(--el-text-color-placeholder); font-size: 13px; }
:deep(.row-incomplete) > td { background: var(--el-color-danger-light-9) !important; }
.drawer-head-card {
  display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;
  padding: 12px 14px; border-radius: 10px; margin-bottom: 12px; border: 1px solid var(--el-border-color);
}
.drawer-head-card.red { background: var(--el-color-danger-light-9); border-color: var(--el-color-danger-light-5); }
.drawer-head-card.done { background: var(--el-color-success-light-9); border-color: var(--el-color-success-light-5); }
.dh-title { font-size: 15px; font-weight: 700; margin-bottom: 4px; }
.dh-meta { font-size: 12px; color: var(--el-text-color-regular); line-height: 1.6; }
.check-item-list { display: flex; flex-direction: column; gap: 10px; }
.ci-card {
  padding: 12px 14px; border-radius: 10px; border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-blank); display: flex; flex-direction: column; gap: 8px;
}
.ci-card.unchecked { border-color: var(--el-color-danger-light-5); background: var(--el-color-danger-light-9); }
.ci-card.checked { border-color: var(--el-color-success-light-5); }
.ci-card.abnormal { border-color: var(--el-color-warning); background: var(--el-color-warning-light-9); }
.ci-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.ci-title { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 14px; }
.seq-badge {
  display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px;
  border-radius: 50%; background: var(--el-color-primary); color: #fff; font-size: 12px; font-weight: 700;
}
.ci-std { font-size: 12px; color: var(--el-text-color-secondary); }
.photo-row { display: flex; align-items: flex-start; gap: 10px; flex-wrap: wrap; }
.photo-thumb {
  position: relative; width: 96px; height: 72px; border-radius: 6px; overflow: hidden;
  border: 1px solid var(--el-border-color); cursor: pointer; background: #0f172a;
  display: flex; align-items: center; justify-content: center;
}
.photo-thumb img { width: 100%; height: 100%; object-fit: cover; }
.loading-img { color: #cbd5e1; font-size: 11px; }
.del-p { position: absolute; right: 2px; top: 2px; width: 18px; height: 18px; min-height: 18px; padding: 0; }
.photo-meta { font-size: 12px; color: var(--el-text-color-regular); line-height: 1.6; }
.abn-txt { color: #dc2626; font-weight: 600; }
.ci-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.cap-info {
  padding: 8px 12px; border-radius: 8px; background: var(--el-fill-color-lighter);
  font-size: 12px; line-height: 1.7; margin-bottom: 10px;
}
.cap-uploaded-list { margin-top: 10px; padding-top: 8px; border-top: 1px dashed var(--el-border-color); }
.cap-uploaded-list .sub { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 6px; }
.ins-pick-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.items-editor-head { display: flex; align-items: center; justify-content: space-between; margin: 10px 0 8px; font-size: 13px; }
.big-preview { display: flex; justify-content: center; background: #0f172a; border-radius: 8px; padding: 8px; }
.big-preview img { max-width: 100%; max-height: 75vh; object-fit: contain; }
</style>
