<template>
  <div class="page-container mkt-public-sea">
    <div class="sea-banner">
      <div class="sea-banner-text">
        <b>内部自营公海弹药库</b>：仅对园区内部招商专员与加盟云仓主账号开放（对外部 P1–P4 完全隔离）；公海成单免切 70% 外部团队佣金，仅计提云仓 P0 15% 跟单奖。
      </div>
      <div class="sea-banner-actions">
        <el-button v-if="canEdit" type="primary" @click="openWashDialog">Excel 防撞清洗导入</el-button>
      </div>
    </div>

    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    >
      <template #default>
        <el-button size="small" type="primary" style="margin-top: 6px" @click="reloadAll">重新加载公海数据</el-button>
      </template>
    </el-alert>

    <!-- 3 张核心分析结论卡（L1 结论卡面，右上角固定挂「下钻 ›」） -->
    <section class="analysis-grid" aria-label="公海池经营分析结论">
      <article v-for="card in analysisCards" :key="card.key" class="analysis-card" :data-analysis-key="card.key">
        <div class="analysis-head">
          <h2 class="analysis-title">{{ card.title }}</h2>
          <button
            type="button"
            class="drilldown-trigger"
            :aria-label="`${card.title} 下钻查看判据与物证`"
            @click="openDrilldown(card.key)"
          >
            下钻 ›
          </button>
        </div>
        <div class="analysis-main">
          <span class="analysis-value">{{ card.primaryText }}</span>
          <el-tag :type="card.badgeType" size="small" effect="light">{{ card.badgeText }}</el-tag>
        </div>
        <div class="analysis-foot">{{ card.footText }}</div>
      </article>
    </section>

    <!-- 筛选栏（纯查询控件，按反向约束不挂下钻） -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" class="sea-filter-form">
        <el-form-item label="数据源">
          <el-select v-model="query.seaSource" style="width: 195px" @change="search">
            <el-option value="ALL" label="全部 5 大数据源" />
            <el-option value="CALC_HOT" label="🔥 30秒算价器留资" />
            <el-option value="CHANMAMA" label="🎯 蝉妈妈低物流分+探迹" />
            <el-option value="TUNGEE_JOB" label="📦 招打包工特征库" />
            <el-option value="EXPIRE_RETURN" label="♻️ 伙伴超期/弃单回流" />
            <el-option value="PARK_CRM" label="🏢 园区看房&云仓调剂" />
          </el-select>
        </el-form-item>
        <el-form-item label="认领状态">
          <el-select v-model="query.claimStatus" style="width: 155px" @change="search">
            <el-option value="ALL" label="全部状态" />
            <el-option value="UNCLAIMED" label="待认领跟进" />
            <el-option value="CLAIMED_ADMIN" label="专员跟进中" />
            <el-option value="FEED_WH" label="已投喂云仓池" />
            <el-option value="CLAIMED_WH" label="云仓已认领" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="企业名/网店/类目/手机" style="width: 200px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 列表区：桌面表格 + 手机卡片自适应 -->
    <div class="table-card" v-loading="loading">
      <div class="toolbar">
        <span class="hint">
          认领后启动 {{ summaryData.slaHours || 72 }} 小时首触达保活倒计时；提交首通核实纪要即解除自动弹回。云仓端每日限领 {{ summaryData.whDailyCap || 5 }} 户 / 在途上限 {{ summaryData.whActiveCap || 10 }} 户。
        </span>
      </div>

      <el-empty v-if="!loading && !list.length" description="当前筛选条件下暂无公海待跟进货主">
        <el-button v-if="canEdit" type="primary" @click="openWashDialog">导入清洗名单补充活水</el-button>
      </el-empty>

      <template v-else>
        <div class="desktop-table-wrap">
          <el-table :data="list" border stripe>
            <el-table-column label="货主企业 / 关联店铺 / 电话" min-width="220">
              <template #default="{ row }">
                <div class="row-title">{{ row.name }}</div>
                <div v-if="row.shopName" class="sub-note">{{ row.shopName }}</div>
                <div class="sub-note">
                  {{ row.contact || '负责人' }} · <b>{{ row.phone }}</b>
                  <el-tag v-if="row.phoneUnlocked" type="success" size="small" effect="plain" style="margin-left: 6px">已解锁</el-tag>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="数据来源" width="165">
              <template #default="{ row }">
                <el-tag :type="row.seaSource === 'CALC_HOT' ? 'danger' : 'info'" size="small">
                  {{ SOURCE_LABELS[row.seaSource] || row.seaSource }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="货盘画像与上轮卡点" min-width="230">
              <template #default="{ row }">
                <div>
                  <b>{{ row.category || '综合电商' }}</b>
                  <span v-if="row.estDailyOrders"> · 日估 {{ row.estDailyOrders }} 单</span>
                  <span v-if="row.avgWeightKg"> · 均重 {{ row.avgWeightKg }}kg</span>
                </div>
                <div class="sub-note">{{ row.blockNote || '待首通电话核实实际单量与搬仓窗口' }}</div>
              </template>
            </el-table-column>

            <el-table-column label="换仓痛点评级" width="165">
              <template #default="{ row }">
                <button type="button" class="urgency-pill" :data-level="row.urgencyLevel || 'B'" @click="openUrgencyDrawer(row)">
                  {{ row.urgencyLevel || 'B' }}级{{ URGENCY_TEXT[row.urgencyLevel] || '储备' }}
                  <span v-if="row.logisticsScore"> · {{ row.logisticsScore }}分</span> ›
                </button>
                <div class="sub-note">0 外部佣金 · 15% P0奖</div>
              </template>
            </el-table-column>

            <el-table-column label="跟进与保活时效" width="175">
              <template #default="{ row }">
                <el-tag :type="claimTagType(row)" size="small">{{ CLAIM_LABELS[row.claimStatus] || row.claimStatus }}</el-tag>
                <div v-if="row.claimHolderName" class="sub-note">{{ row.claimHolderName }}</div>
                <div v-if="row.firstTouchedAt" class="sub-note ok-text">✔ 已首触达保活</div>
                <div v-else-if="row.hoursLeft != null" class="sub-note" :class="{ 'warn-text': row.hoursLeft <= 12 }">
                  ⏱ 剩 {{ row.hoursLeft }}h 首触达期限
                </div>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="235" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canEdit && ['UNCLAIMED', 'FEED_WH'].includes(row.claimStatus)"
                  type="primary"
                  size="small"
                  @click="handleClaim(row)"
                >
                  认领电销
                </el-button>
                <el-button
                  v-else-if="canEdit"
                  size="small"
                  @click="openTouchDrawer(row)"
                >
                  首通纪要
                </el-button>
                <el-button v-if="canEdit" link type="primary" @click="openFeedDialog(row)">投喂云仓</el-button>
                <el-button link type="primary" @click="openTouchDrawer(row)">详情比价</el-button>
                <el-button v-if="canEdit" link type="warning" @click="openReleaseDialog(row)">释放</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 手机窄视口自适应卡片列表（防 390px 横向溢出与遮挡） -->
        <div class="mobile-card-list">
          <div v-for="row in list" :key="row.customerId" class="mobile-sea-card">
            <div class="m-card-top">
              <b>{{ row.name }}</b>
              <el-tag :type="row.seaSource === 'CALC_HOT' ? 'danger' : 'info'" size="small">
                {{ SOURCE_LABELS[row.seaSource] || row.seaSource }}
              </el-tag>
            </div>
            <div class="sub-note">{{ row.shopName || '未填店铺名' }} · {{ row.category || '综合电商' }}</div>
            <div class="sub-note">
              <span v-if="row.estDailyOrders">日估 {{ row.estDailyOrders }} 单 · </span>
              {{ row.contact || '负责人' }} {{ row.phone }}
            </div>
            <div class="m-card-tags">
              <button type="button" class="urgency-pill" :data-level="row.urgencyLevel || 'B'" @click="openUrgencyDrawer(row)">
                {{ row.urgencyLevel || 'B' }}级痛点判据 ›
              </button>
              <el-tag :type="claimTagType(row)" size="small">{{ CLAIM_LABELS[row.claimStatus] || row.claimStatus }}</el-tag>
              <span v-if="!row.firstTouchedAt && row.hoursLeft != null" class="sub-note">剩 {{ row.hoursLeft }}h</span>
            </div>
            <div class="m-card-actions">
              <el-button
                v-if="canEdit && ['UNCLAIMED', 'FEED_WH'].includes(row.claimStatus)"
                type="primary"
                size="small"
                @click="handleClaim(row)"
              >
                认领解锁电话
              </el-button>
              <el-button size="small" @click="openTouchDrawer(row)">纪要与比价</el-button>
              <el-button v-if="canEdit" size="small" @click="openFeedDialog(row)">投喂云仓</el-button>
            </div>
          </div>
        </div>

        <el-pagination
          class="pager"
          background
          layout="total, prev, pager, next, sizes"
          :total="total"
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          @change="loadPage"
        />
      </template>
    </div>

    <!-- 三层下钻抽屉（L1 核心问题 -> L2 判据列表 -> L3 五联物证） -->
    <el-drawer
      v-model="drill.visible"
      :title="activeDrillCard ? `下钻溯源 · ${activeDrillCard.title}` : '下钻溯源'"
      size="min(560px, 94vw)"
      append-to-body
    >
      <div v-if="activeDrillCard" class="drill-body">
        <div class="drill-box">
          <div class="drill-box-head">
            <span class="layer-chip">L1 核心结论</span>
            <el-tag :type="activeDrillCard.badgeType" size="small">{{ activeDrillCard.badgeText }}</el-tag>
          </div>
          <p class="drill-q">{{ activeDrillCard.question }}</p>
          <div>当前结论值：<b>{{ activeDrillCard.primaryText }}</b></div>
        </div>

        <div class="drill-box">
          <span class="layer-chip">L2 判定逻辑（同源搬运 · 零二次重算）</span>
          <p class="drill-q" style="margin-bottom: 0">{{ activeDrillCard.logicNote }}</p>
        </div>

        <div class="drill-tip-row">
          <b>L2 逐条判据列表</b>
          <span class="sub-note">点击任意行展开 L3 五联物证</span>
        </div>

        <div v-for="r in activeDrillCard.rows" :key="r.key" class="l2-item">
          <button type="button" class="l2-summary" @click="toggleL3(r.key)">
            <div>
              <div class="row-title">{{ r.label }}</div>
              <div class="sub-note">{{ r.verdict }}</div>
            </div>
            <div class="l2-right">
              <el-tag size="small" type="primary" effect="plain">{{ r.value }}</el-tag>
              <span class="expand-hint">{{ expandedKeys.includes(r.key) ? '收起 ▴' : 'L3物证 ▾' }}</span>
            </div>
          </button>
          <div v-if="expandedKeys.includes(r.key) && r.evidence" class="l3-panel">
            <div><b>① 原始数值表</b>：<code>{{ r.evidence.rawTable }}</code></div>
            <div><b>② 来源与抓取</b>：{{ r.evidence.sourceNote }}</div>
            <div v-if="r.evidence.formula"><b>③ 带实参公式</b>：<code>{{ r.evidence.formula }}</code></div>
            <div><b>④ 透明标注</b>：{{ r.evidence.aiNote }}</div>
            <div v-if="r.evidence.actionLabel" style="margin-top: 6px">
              <el-button link type="primary" @click="runDrillAction(r.evidence)">
                {{ r.evidence.actionLabel }} ›
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 行内换仓痛点评级下钻抽屉 -->
    <el-drawer v-model="urgencyDrawer.visible" title="行内结论下钻 · 换仓痛点评级依据" size="min(480px, 94vw)" append-to-body>
      <div v-if="urgencyDrawer.row" class="drill-body">
        <div class="drill-box">
          <span class="layer-chip">L1 评级结论</span>
          <h3 style="margin: 6px 0">{{ urgencyDrawer.row.name }} · {{ urgencyDrawer.row.urgencyLevel }}级</h3>
          <div class="sub-note">{{ urgencyDrawer.row.urgencyReason || '公海标准线索，待首通电话核实' }}</div>
        </div>
        <div class="drill-box">
          <span class="layer-chip">L3 原始物证快照</span>
          <div class="sub-note" style="margin-top: 6px">
            <code>seaSource={{ urgencyDrawer.row.seaSource }}, category={{ urgencyDrawer.row.category || '—' }}, estDailyOrders={{ urgencyDrawer.row.estDailyOrders ?? '—' }}, logisticsScore={{ urgencyDrawer.row.logisticsScore ?? '—' }}</code>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 客户公海档案、首通核实纪要与多仓价卡比价抽屉 -->
    <el-drawer v-model="touchDrawer.visible" :title="touchDrawer.row?.name || '公海跟进与比价'" size="min(640px, 94vw)" append-to-body>
      <div v-if="touchDrawer.row" class="drill-body">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="货主 / 网店">{{ touchDrawer.row.name }}（{{ touchDrawer.row.shopName || '未填网店' }}）</el-descriptions-item>
          <el-descriptions-item label="联系方式">{{ touchDrawer.row.contact || '负责人' }} · {{ touchDrawer.row.phone }}</el-descriptions-item>
          <el-descriptions-item label="当前卡点">{{ touchDrawer.row.blockNote || '暂无记录' }}</el-descriptions-item>
          <el-descriptions-item v-if="touchDrawer.row.calcLowPrice" label="算价器参考底价">
            ¥ {{ touchDrawer.row.calcLowPrice }} ~ ¥ {{ touchDrawer.row.calcHighPrice }} / 单
          </el-descriptions-item>
        </el-descriptions>

        <CustomerQuotePanel
          v-if="touchDrawer.visible && effectiveProjectId"
          :key="`${effectiveProjectId}/${touchDrawer.row.customerId}`"
          :customer-id="touchDrawer.row.customerId"
          :project-id="effectiveProjectId"
        />

        <el-form v-if="canEdit" label-position="top" style="margin-top: 10px">
          <el-form-item label="核实后主营类目">
            <el-input v-model="touchDrawer.category" maxlength="64" placeholder="如：女装服饰 / 美妆个护 / 休闲食品" />
          </el-form-item>
          <el-form-item label="核实日均发货单量（单/天）">
            <el-input-number v-model="touchDrawer.estDailyOrders" :min="0" :max="200000" style="width: 100%" />
          </el-form-item>
          <el-form-item label="首通电话核实纪要（保存即解除 72h 超时弹回）" required>
            <el-input
              v-model="touchDrawer.note"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="请记录实际日单量、件重、当前仓库痛点与意向试发时间..."
            />
          </el-form-item>
          <div style="display: flex; gap: 8px; justify-content: flex-end">
            <el-button @click="touchDrawer.visible = false">关闭</el-button>
            <el-button type="primary" :loading="touchDrawer.busy" @click="submitFirstTouch">保存首通核实纪要</el-button>
          </div>
        </el-form>
      </div>
    </el-drawer>

    <!-- Excel / 名单三道闸防撞清洗导入弹窗 -->
    <el-dialog v-model="washDialog.visible" title="外部数据源导入与三道闸防撞洗数" width="min(620px, 94vw)">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="入库前强制自动执行三道防撞闸：① 撞 90 天在锁私海跳过护客；② 撞加盟云仓 AES+HMAC 保密老客跳过；③ 剔除关联 >3 家企业的代账财务重号。"
      />
      <el-form label-position="top" style="margin-top: 14px">
        <el-form-item label="数据源渠道标签">
          <el-select v-model="washDialog.seaSource" style="width: 100%">
            <el-option value="CHANMAMA" label="🎯 蝉妈妈/考古加低物流分 + 探迹实名表" />
            <el-option value="TUNGEE_JOB" label="📦 探迹“正在招打包工”特征名单" />
            <el-option value="PARK_CRM" label="🏢 展会参展名录 / 园区物业调剂名单" />
            <el-option value="MANUAL_IMPORT" label="📋 其他合规拓客名单" />
          </el-select>
        </el-form-item>

        <el-radio-group v-model="washDialog.mode" style="margin-bottom: 12px">
          <el-radio-button value="file">上传 Excel / CSV 文件</el-radio-button>
          <el-radio-button value="paste">快捷粘贴文本名单</el-radio-button>
        </el-radio-group>

        <el-form-item v-if="washDialog.mode === 'file'" label="选择标准表格文件（表头含：企业名/店铺名、手机号、类目、日均单量、物流分）">
          <input type="file" accept=".xlsx,.xls,.csv" @change="onPickWashFile" />
        </el-form-item>
        <el-form-item v-else label="每行一条：企业名称,手机号,类目,日均单量,物流分（逗号分隔）">
          <el-input
            v-model="washDialog.pasteText"
            type="textarea"
            :rows="5"
            placeholder="杭州衣恋服饰有限公司,13857198821,女装服饰,1200,4.42&#10;湖州肌研生物科技,13967203319,美妆个护,950,4.48"
          />
        </el-form-item>
      </el-form>

      <div v-if="washDialog.preview" class="wash-result-box">
        <div><b>预检批次统计（总计 {{ washDialog.preview.totalRows }} 行）</b>：</div>
        <div class="wash-stats-grid">
          <div>🛡️ 护在锁私海拦截：<b>{{ washDialog.preview.blockedPrivateLock }}</b> 行</div>
          <div>🔐 护云仓 HMAC 老客：<b>{{ washDialog.preview.blockedWhHmac }}</b> 行</div>
          <div>🧹 剔除代账重号/冷宫：<b>{{ washDialog.preview.blockedDupAgency }}</b> 行</div>
          <div class="ok-text">✅ 干净净增可入公海：<b>{{ washDialog.preview.insertedRows }}</b> 户</div>
        </div>
      </div>

      <template #footer>
        <el-button :disabled="washDialog.busy" @click="washDialog.visible = false">取消</el-button>
        <el-button :loading="washDialog.busy" @click="submitWash(true)">先预检防撞结果</el-button>
        <el-button type="primary" :loading="washDialog.busy" @click="submitWash(false)">确认洗数入池</el-button>
      </template>
    </el-dialog>

    <!-- 定向投喂加盟云仓限时认领池弹窗 -->
    <el-dialog v-model="feedDialog.visible" title="定向投喂至加盟云仓限时认领池" width="min(520px, 94vw)">
      <p class="sub-note" style="margin-top: 0">
        将「<b>{{ feedDialog.row?.name }}</b>」开放给已上线加盟云仓主账号限时认领（认领前隐藏客户电话，认领后 {{ summaryData.slaHours || 72 }}h 内须回填接洽结果，成单免 70% 外部佣金）：
      </p>
      <el-form label-position="top">
        <el-form-item label="限定可认领云仓（留空表示开放给本园区全部已上线云仓）">
          <el-select v-model="feedDialog.warehouseIds" multiple filterable clearable placeholder="全部本园区已上线加盟云仓" style="width: 100%">
            <el-option v-for="w in onlineWarehouses" :key="w.id" :value="w.id" :label="w.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="给云仓的货盘与卡点提示">
          <el-input v-model="feedDialog.note" type="textarea" :rows="3" maxlength="400" placeholder="如：日均 1200 单女装，急需下周 500 件爆款试发..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="feedDialog.busy" @click="feedDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="feedDialog.busy" @click="submitFeed">确认投喂</el-button>
      </template>
    </el-dialog>

    <!-- 主动释放或归档冷宫弹窗 -->
    <el-dialog v-model="releaseDialog.visible" title="释放回公海或归档冷宫" width="min(460px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="释放原因 / 最新未成卡点（必填，供下一任专员或换仓参考）" required>
          <el-input v-model="releaseDialog.reason" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="如：嫌常温首重高 0.15 元，建议改推经济仓..." />
        </el-form-item>
        <el-checkbox v-model="releaseDialog.archiveCold">确认已停业/已自建仓/无效空号，直接转入冷宫归档库</el-checkbox>
      </el-form>
      <template #footer>
        <el-button :disabled="releaseDialog.busy" @click="releaseDialog.visible = false">取消</el-button>
        <el-button type="warning" :loading="releaseDialog.busy" @click="submitRelease">确认释放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { hasPermission } from '@/utils/permission'
import { useProjectStore } from '@/stores/project'
import { mktPublicSeaApi, mktWarehouseApi } from '@/api/marketing'
import CustomerQuotePanel from './CustomerQuotePanel.vue'
import { buildPublicSeaDrilldownCards } from './publicSeaDrilldown'

const router = useRouter()
const projectStore = useProjectStore()
const canEdit = computed(() => hasPermission('crm:marketing:public-sea:edit') || hasPermission('crm:marketing:customer:edit'))
const effectiveProjectId = computed(() => projectStore.currentProjectId || null)

const SOURCE_LABELS = {
  CALC_HOT: '🔥 30秒算价留资',
  CHANMAMA: '🎯 蝉妈妈+探迹',
  TUNGEE_JOB: '📦 招打包工库',
  EXPIRE_RETURN: '♻️ 伙伴超期回流',
  PARK_CRM: '🏢 园区/云仓调剂',
  MANUAL_IMPORT: '📋 批量清洗导入'
}
const CLAIM_LABELS = {
  UNCLAIMED: '待认领跟进',
  CLAIMED_ADMIN: '专员跟进中',
  FEED_WH: '已投喂云仓池',
  CLAIMED_WH: '云仓已认领'
}
const URGENCY_TEXT = { S: '急迫', A: '高潜', B: '储备' }

const summaryData = ref({})
const list = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const onlineWarehouses = ref([])

const query = reactive({
  seaSource: 'ALL',
  claimStatus: 'ALL',
  keyword: '',
  pageNo: 1,
  pageSize: 20
})

const analysisCards = computed(() => buildPublicSeaDrilldownCards(summaryData.value))
const drill = reactive({ visible: false, key: 'pool' })
const expandedKeys = ref([])
const activeDrillCard = computed(() => analysisCards.value.find(c => c.key === drill.key) || null)

function openDrilldown(key) {
  drill.key = key
  const card = analysisCards.value.find(c => c.key === key)
  expandedKeys.value = card?.rows?.[0] ? [card.rows[0].key] : []
  drill.visible = true
}

function toggleL3(rowKey) {
  const idx = expandedKeys.value.indexOf(rowKey)
  if (idx >= 0) expandedKeys.value.splice(idx, 1)
  else expandedKeys.value.push(rowKey)
}

function runDrillAction(ev) {
  drill.visible = false
  if (ev.toPath) { router.push(ev.toPath); return }
  if (ev.openImport) { openWashDialog(); return }
  if (ev.filterSource) { query.seaSource = ev.filterSource; query.claimStatus = 'ALL'; search(); return }
  if (ev.filterClaimStatus) { query.claimStatus = ev.filterClaimStatus; query.seaSource = 'ALL'; search() }
}

function claimTagType(row) {
  if (row.claimStatus === 'UNCLAIMED') return 'primary'
  if (!row.firstTouchedAt && row.hoursLeft != null && row.hoursLeft <= 12) return 'warning'
  return 'success'
}

async function loadSummary() {
  summaryData.value = await mktPublicSeaApi.summary({ projectId: effectiveProjectId.value })
}

async function loadPage() {
  loading.value = true
  try {
    const res = await mktPublicSeaApi.page({
      projectId: effectiveProjectId.value,
      seaSource: query.seaSource,
      claimStatus: query.claimStatus,
      keyword: query.keyword || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    })
    list.value = res?.records || []
    total.value = Number(res?.total || 0)
  } finally {
    loading.value = false
  }
}

async function reloadAll() {
  loadError.value = ''
  try {
    await Promise.all([loadSummary(), loadPage()])
  } catch (e) {
    loadError.value = e?.message || '内部公海池数据加载失败，请重试'
  }
}

function search() {
  query.pageNo = 1
  reloadAll()
}

function resetQuery() {
  query.seaSource = 'ALL'
  query.claimStatus = 'ALL'
  query.keyword = ''
  search()
}

async function handleClaim(row) {
  try {
    const res = await mktPublicSeaApi.claim(row.customerId)
    ElMessage.success(`认领成功，已解锁手机号 ${res.phone || ''}，请于 ${res.hoursLeft || 72}h 内提交首通核实纪要`)
    await reloadAll()
  } catch (e) { /* request.js 已统一弹错 */ }
}

const urgencyDrawer = reactive({ visible: false, row: null })
function openUrgencyDrawer(row) {
  urgencyDrawer.row = row
  urgencyDrawer.visible = true
}

const touchDrawer = reactive({ visible: false, row: null, category: '', estDailyOrders: 0, note: '', busy: false })
function openTouchDrawer(row) {
  touchDrawer.row = row
  touchDrawer.category = row.category || ''
  touchDrawer.estDailyOrders = row.estDailyOrders || 0
  touchDrawer.note = row.firstTouchNote || ''
  touchDrawer.visible = true
}

async function submitFirstTouch() {
  if (!touchDrawer.row || touchDrawer.busy) return
  touchDrawer.busy = true
  try {
    await mktPublicSeaApi.firstTouch(touchDrawer.row.customerId, {
      note: touchDrawer.note,
      category: touchDrawer.category,
      estDailyOrders: touchDrawer.estDailyOrders || undefined
    })
    ElMessage.success('首通核实纪要已保存，超时自动弹回预警已解除')
    touchDrawer.visible = false
    await reloadAll()
  } finally {
    touchDrawer.busy = false
  }
}

const washDialog = reactive({
  visible: false, mode: 'file', seaSource: 'CHANMAMA',
  file: null, pasteText: '', preview: null, busy: false
})
function openWashDialog() {
  Object.assign(washDialog, { visible: true, file: null, pasteText: '', preview: null, busy: false })
}
function onPickWashFile(ev) {
  washDialog.file = ev.target?.files?.[0] || null
  washDialog.preview = null
}

function parsePasteRows(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map(l => l.trim())
    .filter(Boolean)
    .map(line => {
      const [name, phone, category, daily, score] = line.split(/[，,\t]/).map(s => s?.trim())
      return {
        name,
        shopName: name,
        phone,
        category: category || '综合电商',
        estDailyOrders: daily ? Number(daily) : undefined,
        logisticsScore: score || undefined
      }
    })
}

async function submitWash(dryRun) {
  if (washDialog.busy) return
  washDialog.busy = true
  try {
    let res
    if (washDialog.mode === 'file') {
      if (!washDialog.file) { ElMessage.warning('请先选择 Excel 或 CSV 名单文件'); return }
      const fd = new FormData()
      fd.append('file', washDialog.file)
      res = await mktPublicSeaApi.washFile(fd, {
        projectId: effectiveProjectId.value || undefined,
        seaSource: washDialog.seaSource,
        dryRun
      })
    } else {
      const rows = parsePasteRows(washDialog.pasteText)
      if (!rows.length) { ElMessage.warning('请先输入至少 1 行待清洗名单'); return }
      res = await mktPublicSeaApi.washRows({
        projectId: effectiveProjectId.value || undefined,
        seaSource: washDialog.seaSource,
        fileName: '文本快捷清洗',
        dryRun,
        rows
      })
    }
    washDialog.preview = res
    if (!dryRun) {
      ElMessage.success(`清洗入池完成：净入公海 ${res.insertedRows} 户（防撞拦截 ${res.totalRows - res.insertedRows} 行）`)
      washDialog.visible = false
      await reloadAll()
    }
  } finally {
    washDialog.busy = false
  }
}

const feedDialog = reactive({ visible: false, row: null, warehouseIds: [], note: '', busy: false })
async function ensureWarehousesLoaded() {
  if (onlineWarehouses.value.length) return
  try {
    const res = await mktWarehouseApi.page({ pageNo: 1, pageSize: 100, joinStatus: 5, projectId: effectiveProjectId.value || undefined })
    onlineWarehouses.value = res?.records || []
  } catch (_) { onlineWarehouses.value = [] }
}
async function openFeedDialog(row) {
  await ensureWarehousesLoaded()
  Object.assign(feedDialog, { visible: true, row, warehouseIds: [], note: row.blockNote || '', busy: false })
}
async function submitFeed() {
  if (!feedDialog.row || feedDialog.busy) return
  feedDialog.busy = true
  try {
    await mktPublicSeaApi.feedWarehouses(feedDialog.row.customerId, {
      warehouseIds: feedDialog.warehouseIds,
      note: feedDialog.note
    })
    ElMessage.success('已定向投喂至加盟云仓限时认领池')
    feedDialog.visible = false
    await reloadAll()
  } finally {
    feedDialog.busy = false
  }
}

const releaseDialog = reactive({ visible: false, row: null, reason: '', archiveCold: false, busy: false })
function openReleaseDialog(row) {
  Object.assign(releaseDialog, { visible: true, row, reason: '', archiveCold: false, busy: false })
}
async function submitRelease() {
  if (!releaseDialog.row || releaseDialog.busy) return
  releaseDialog.busy = true
  try {
    await mktPublicSeaApi.release(releaseDialog.row.customerId, {
      reason: releaseDialog.reason,
      archiveCold: releaseDialog.archiveCold
    })
    ElMessage.success(releaseDialog.archiveCold ? '已归档至冷宫库' : '已释放回公海待认领池')
    releaseDialog.visible = false
    await reloadAll()
  } finally {
    releaseDialog.busy = false
  }
}

watch(() => projectStore.currentProjectId, () => { query.pageNo = 1; reloadAll() })
onMounted(reloadAll)
</script>

<style scoped>
.sea-banner {
  background: linear-gradient(90deg, #eff6ff, #f8fafc);
  border: 1px solid #bfdbfe;
  color: #1e3a8a;
  border-radius: 10px;
  padding: 12px 16px;
  margin-bottom: 14px;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.analysis-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}
.analysis-card {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter, #e2e8f0);
  border-radius: 10px;
  padding: 14px 16px;
}
.analysis-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.analysis-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}
.drilldown-trigger {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.analysis-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 8px 0 4px;
  flex-wrap: wrap;
}
.analysis-value {
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
}
.analysis-foot, .sub-note {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}
.row-title { font-weight: 600; color: #0f172a; }
.ok-text { color: #16a34a; font-weight: 600; }
.warn-text { color: #d97706; font-weight: 600; }
.urgency-pill {
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: #b45309;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.urgency-pill[data-level="S"] {
  border-color: #fecdd3;
  background: #fff1f2;
  color: #e11d48;
}
.pager { margin-top: 14px; justify-content: flex-end; }
.drill-body { display: flex; flex-direction: column; gap: 12px; }
.drill-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
}
.drill-box-head, .drill-tip-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.layer-chip {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  color: #1d4ed8;
  background: #dbeafe;
  padding: 2px 7px;
  border-radius: 4px;
}
.drill-q { margin: 6px 0; font-size: 13px; color: #334155; }
.l2-item { border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden; }
.l2-summary {
  width: 100%;
  border: 0;
  background: #f8fafc;
  padding: 10px 12px;
  text-align: left;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}
.l2-right { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.expand-hint { font-size: 11px; color: #2563eb; }
.l3-panel {
  padding: 10px 12px;
  background: #fff;
  border-top: 1px dashed #e2e8f0;
  font-size: 12.5px;
  color: #475569;
  display: grid;
  gap: 5px;
}
.wash-result-box {
  margin-top: 12px;
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 12px;
  font-size: 13px;
}
.wash-stats-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 8px;
}
.mobile-card-list { display: none; flex-direction: column; gap: 10px; }
.mobile-sea-card { border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px; background: #fff; }
.m-card-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; margin-bottom: 4px; }
.m-card-tags { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin: 8px 0; }
.m-card-actions { display: flex; flex-wrap: wrap; gap: 8px; }

@media (max-width: 768px) {
  .analysis-grid { grid-template-columns: 1fr; }
  .desktop-table-wrap { display: none; }
  .mobile-card-list { display: flex; }
  .wash-stats-grid { grid-template-columns: 1fr; }
}
</style>
