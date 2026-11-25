<template>
  <div class="page-container mkt-public-sea">
    <!-- 诚实连接器未配 Key 状态条（绝不伪造企查查或高德实景数据） -->
    <div v-if="showConnectorHint" class="connector-honest-bar">
      <div>
        🔑 <b>外部连接器状态</b>：
        高德地理编码/POI雷达 <b>{{ connectors.amapWebConfigured ? '已启用' : '待填免费Key' }}</b> ·
        轻量工商/网店对齐 <b>{{ connectors.qccConfigured ? '已启用(30天缓存)' : '待填百元包AppCode（当前仅存基础线索，不伪造核验数据）' }}</b> ·
        飞鱼免费Webhook <b>已就绪</b>
      </div>
      <el-button link type="primary" @click="router.push('/crm/marketing/setting?tab=connectors')">
        🔌 18通道连接器中心 ({{ connectors.activeConnectors || 4 }}/{{ connectors.totalConnectors || 18 }}) ›
      </el-button>
    </div>

    <!-- 第1层：双视图切换 + 广东定向省钱硬锁 + 三大低成本获客武器 -->
    <div class="sea-banner">
      <div class="sea-banner-left">
        <div class="view-mode-seg" role="tablist" aria-label="公海列表与高德地图双视图切换">
          <button
            type="button"
            class="seg-btn"
            :class="{ active: viewMode === 'list' }"
            data-testid="mode-list"
            @click="viewMode = 'list'"
          >
            📋 列表电销视图 ({{ total }})
          </button>
          <button
            type="button"
            class="seg-btn"
            :class="{ active: viewMode === 'map' }"
            data-testid="mode-map"
            @click="switchToMapView"
          >
            🗺️ 广东高德地推地图
          </button>
        </div>
        <span class="gd-lock-badge">
          💰 {{ connectors.regionLockProvince || '广东省' }}定向省钱锁：外省主体自动拦截免扣API费 · 成单0外部佣金
        </span>
      </div>
      <div v-if="canEdit" class="sea-banner-actions">
        <el-button size="small" @click="openPullLeadsDialog" data-testid="btn-pull-leads">🤖 蝉妈妈/店透巢/探迹拉榜</el-button>
        <el-button type="warning" plain size="small" @click="openRadarDialog">📡 高德广东商圈扫楼雷达</el-button>
        <el-button type="success" plain size="small" @click="openShopLookupDialog">🛍️ 单店名反查公司</el-button>
        <el-button type="primary" size="small" @click="openWashDialog">🏆 类目TOP500店铺榜对齐入池</el-button>
      </div>
    </div>

    <!-- 广东主力战区产业带分片区快切条 -->
    <div class="gd-zone-bar" role="region" aria-label="广东主力战区分区快切">
      <div class="gd-zone-buttons">
        <span class="gd-zone-title">📍 广东主力战区分区：</span>
        <button
          v-for="z in GD_ZONES"
          :key="z.code"
          type="button"
          class="zone-chip"
          :class="{ active: query.zoneCode === z.code }"
          :data-zone="z.code"
          @click="selectZone(z.code)"
        >
          {{ z.label }}
          <span v-if="zoneCountText(z.code)" class="zone-cnt">{{ zoneCountText(z.code) }}</span>
        </button>
      </div>
      <span class="sub-note">🔒 严隔外部 P1–P4 推广员</span>
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

    <!-- 第2层：4 张核心分析结论卡（L1 结论卡面，右上角固定挂「下钻 ›」） -->
    <section class="analysis-grid" aria-label="公海池经营与广东地推分析结论">
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

    <!-- 第3层：多维电销+高德地推筛选栏（纯查询控件，不挂下钻） -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" class="sea-filter-form">
        <el-form-item label="数据源">
          <el-select v-model="query.seaSource" style="width: 175px" @change="search">
            <el-option value="ALL" label="全部数据源" />
            <el-option value="CALC_HOT" label="🔥 30秒算价器留资" />
            <el-option value="FEIYU_AD" label="📣 巨量飞鱼免费Webhook" />
            <el-option value="AMAP_POI" label="📡 高德周边POI扫楼" />
            <el-option value="CHANMAMA" label="🏆 店铺榜企查对齐" />
            <el-option value="TUNGEE_JOB" label="📦 招打包工特征库" />
            <el-option value="EXPIRE_RETURN" label="♻️ 伙伴超期回流" />
            <el-option value="PARK_CRM" label="🏢 园区/云仓调剂" />
          </el-select>
        </el-form-item>
        <el-form-item label="高德距离">
          <el-select v-model="query.distFilter" style="width: 160px" @change="search">
            <el-option value="ALL" label="不限里程" />
            <el-option value="5KM" label="⭕ 云仓5km黄金圈内" />
            <el-option value="CLUSTER" label="🏢 同栋大厦聚集(≥2户)" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联平台">
          <el-select v-model="query.platform" style="width: 135px" @change="search">
            <el-option value="ALL" label="全部平台" />
            <el-option value="抖音" label="含抖音小店" />
            <el-option value="天猫" label="含天猫/淘宝店" />
            <el-option value="1688" label="含1688源头厂" />
            <el-option value="拼多多" label="含拼多多店" />
          </el-select>
        </el-form-item>
        <el-form-item label="工商体检">
          <el-select v-model="query.riskFilter" style="width: 160px" @change="search">
            <el-option value="ALL" label="全部主体" />
            <el-option value="CLEAN" label="🟢 0失信且参保≥5人" />
            <el-option value="RISK" label="🔴 失信/限高预警" />
          </el-select>
        </el-form-item>
        <el-form-item label="保活状态">
          <el-select v-model="query.claimStatus" style="width: 150px" @change="search">
            <el-option value="ALL" label="全部状态" />
            <el-option value="UNCLAIMED" label="待认领跟进" />
            <el-option value="VISIT_15D" label="📍 15天地推保护中" />
            <el-option value="CLAIMED_ADMIN" label="专员电销跟进中" />
            <el-option value="FEED_WH" label="已投喂云仓池" />
            <el-option value="CLAIMED_WH" label="云仓已认领" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="企业/网店/大厦路名/手机" style="width: 185px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 勾选批量外勤路线与批量投喂条 -->
      <div v-if="selectedRows.length > 0" class="batch-action-bar">
        <div>
          已勾选 <b>{{ selectedRows.length }}</b> 户广东目标货主（同片区集中扫楼拜访效率最高）
        </div>
        <div class="batch-btns">
          <el-button size="small" type="success" @click="openAmapBatchRoute">
            🚗 高德生成顺路拜访导航（串联 {{ selectedRows.length }} 站）
          </el-button>
          <el-button v-if="canEdit" size="small" type="primary" plain @click="openBatchFeedDialog">
            🏭 批量投喂加盟云仓
          </el-button>
        </div>
      </div>
    </div>

    <!-- 视图 A：列表电销视图（桌面表格 + 手机窄视口卡片自适应） -->
    <div v-if="viewMode === 'list'" class="table-card" v-loading="loading">
      <div class="toolbar">
        <span class="hint">
          电销认领启动 {{ summaryData.slaHours || 72 }}h 首触达倒计时；外勤到达客户现场 ≤{{ summaryData.checkinMaxMeters || 500 }}m 实拍门头打卡即解锁 {{ summaryData.visitProtectDays || 15 }} 天深度地推保护。云仓每日限领 {{ summaryData.whDailyCap || 5 }} 户 / 在途 {{ summaryData.whActiveCap || 10 }} 户。
        </span>
      </div>

      <el-empty v-if="!loading && !list.length" description="当前筛选条件下暂无公海待跟进货主">
        <div style="display: flex; gap: 8px; justify-content: center; flex-wrap: wrap">
          <el-button v-if="canEdit" type="warning" plain @click="openRadarDialog">📡 高德周边10km扫楼挖掘</el-button>
          <el-button v-if="canEdit" type="primary" @click="openWashDialog">🏆 导入类目店铺榜补充活水</el-button>
        </div>
      </el-empty>

      <template v-else>
        <div class="desktop-table-wrap">
          <el-table :data="list" border stripe @selection-change="onSelectionChange">
            <el-table-column type="selection" width="44" />

            <el-table-column label="企业主体 / 工商体检 / 联系方式" min-width="235">
              <template #default="{ row }">
                <div class="row-title link-title" @click="openDetailDrawer(row, 'qcc')">{{ row.name }} ›</div>
                <div v-if="row.qccAligned === 1" class="sub-note">
                  法人 {{ row.legalPerson || '—' }} · {{ row.regCapital || '注册资本待核' }} · <b>参保 {{ row.insuredCount ?? 0 }}人</b>
                </div>
                <div v-else class="sub-note">工商待对齐（可点单店反查或配置百元企查包自动补全）</div>
                <div class="tag-inline-row">
                  <el-tag v-if="row.dishonestCount > 0" type="danger" size="small" @click="openDetailDrawer(row, 'qcc')" style="cursor: pointer">
                    🔴 失信({{ row.dishonestCount }})·禁月结 ›
                  </el-tag>
                  <el-tag v-else-if="row.qccAligned === 1" type="success" size="small" effect="plain">🟢 企查0失信</el-tag>
                  <span class="sub-note">{{ row.contact || '负责人' }} · <b>{{ row.phone }}</b></span>
                  <el-tag v-if="row.phoneUnlocked" type="success" size="small" effect="plain">已解锁</el-tag>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="1客多店电商矩阵" min-width="195">
              <template #default="{ row }">
                <div class="shop-chip-list">
                  <span
                    v-for="(s, idx) in (row.shops || [])"
                    :key="idx"
                    class="shop-chip"
                    :data-plat="s.platform"
                  >
                    {{ s.platform }}: {{ s.shopName }}<template v-if="s.rankNo"> #{{ s.rankNo }}</template>
                  </span>
                  <span v-if="!(row.shops && row.shops.length)" class="sub-note">线下产业带主体</span>
                </div>
                <el-button link type="primary" size="small" @click="openDetailDrawer(row, 'shops')">
                  查看店铺矩阵 ({{ (row.shops || []).length }}) ›
                </el-button>
              </template>
            </el-table-column>

            <el-table-column label="广东地址 · 云仓车程与大厦聚集" min-width="225">
              <template #default="{ row }">
                <div class="addr-line">📍 {{ row.address || `${row.city || '广东'}${row.district || ''}（待落坐标）` }}</div>
                <div class="tag-inline-row">
                  <button
                    v-if="row.distNearestWhKm != null"
                    type="button"
                    class="geo-pill"
                    @click="focusRowOnMap(row)"
                  >
                    🚗 距{{ row.nearestWhName || '本园云仓' }} {{ row.distNearestWhKm }}km · 看图 ›
                  </button>
                  <span v-if="row.buildingName && row.buildingPeerCount >= 2" class="cluster-pill">
                    🏢 {{ row.buildingName }}(同楼{{ row.buildingPeerCount }}户)
                  </span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="货盘画像与换仓痛点评级" min-width="210">
              <template #default="{ row }">
                <div class="tag-inline-row">
                  <el-tag :type="['CALC_HOT', 'FEIYU_AD'].includes(row.seaSource) ? 'danger' : 'info'" size="small">
                    {{ SOURCE_LABELS[row.seaSource] || row.seaSource }}
                  </el-tag>
                  <button type="button" class="urgency-pill" :data-level="row.urgencyLevel || 'B'" @click="openUrgencyDrawer(row)">
                    {{ row.urgencyLevel || 'B' }}级{{ URGENCY_TEXT[row.urgencyLevel] || '储备' }}
                    <span v-if="row.logisticsScore"> · {{ row.logisticsScore }}分</span> ›
                  </button>
                </div>
                <div style="margin-top: 4px">
                  <b>{{ row.category || '综合电商' }}</b>
                  <span v-if="row.estDailyOrders"> · 日估 {{ row.estDailyOrders }} 单</span>
                  <span v-if="row.avgWeightKg"> · {{ row.avgWeightKg }}kg</span>
                </div>
                <div class="sub-note">{{ row.blockNote || '待首通核实单量与搬仓窗口' }}</div>
              </template>
            </el-table-column>

            <el-table-column label="保活与地推延保" width="175">
              <template #default="{ row }">
                <el-tag v-if="row.visitDaysLeft" type="success" size="small">
                  📍 500m打卡保护 · 剩{{ row.visitDaysLeft }}天
                </el-tag>
                <el-tag v-else :type="claimTagType(row)" size="small">
                  {{ CLAIM_LABELS[row.claimStatus] || row.claimStatus }}
                </el-tag>
                <div v-if="row.claimHolderName" class="sub-note">{{ row.claimHolderName }}</div>
                <div v-if="row.lastCheckinDistM != null" class="sub-note ok-text">
                  📸 距址 {{ row.lastCheckinDistM }}m 已实拍打卡
                </div>
                <div v-else-if="row.firstTouchedAt" class="sub-note ok-text">✔ 已首触达保活</div>
                <div v-else-if="row.hoursLeft != null" class="sub-note" :class="{ 'warn-text': row.hoursLeft <= 12 }">
                  ⏱ 剩 {{ row.hoursLeft }}h 须首触达
                </div>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <div class="row-action-col">
                  <div class="row-action-line">
                    <el-button
                      v-if="canEdit && ['UNCLAIMED', 'FEED_WH'].includes(row.claimStatus)"
                      type="primary"
                      size="small"
                      @click="handleClaim(row)"
                    >
                      认领电销
                    </el-button>
                    <el-button
                      v-if="canEdit"
                      type="success"
                      plain
                      size="small"
                      data-testid="btn-visit-checkin"
                      @click="openCheckinDialog(row)"
                    >
                      📍500m打卡
                    </el-button>
                  </div>
                  <div class="row-action-line">
                    <el-button link type="primary" size="small" @click="openDetailDrawer(row, 'quote')">360°与比价</el-button>
                    <el-button v-if="canEdit" link type="primary" size="small" @click="openFeedDialog(row)">投喂云仓</el-button>
                    <el-button v-if="canEdit" link type="warning" size="small" @click="openReleaseDialog(row)">释放</el-button>
                  </div>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 手机外勤端自适应卡片列表（防 390px 横向溢出与遮挡） -->
        <div class="mobile-card-list">
          <div v-for="row in list" :key="row.customerId" class="mobile-sea-card">
            <div class="m-card-top">
              <b class="link-title" @click="openDetailDrawer(row, 'qcc')">{{ row.name }}</b>
              <el-tag :type="['CALC_HOT', 'FEIYU_AD'].includes(row.seaSource) ? 'danger' : 'info'" size="small">
                {{ SOURCE_LABELS[row.seaSource] || row.seaSource }}
              </el-tag>
            </div>
            <div class="shop-chip-list" style="margin: 4px 0">
              <span v-for="(s, idx) in (row.shops || []).slice(0, 2)" :key="idx" class="shop-chip" :data-plat="s.platform">
                {{ s.platform }}: {{ s.shopName }}
              </span>
            </div>
            <div class="sub-note">📍 {{ row.address || row.city || '广东产业带' }}</div>
            <div class="sub-note">
              <span v-if="row.distNearestWhKm != null">距云仓 {{ row.distNearestWhKm }}km · </span>
              <span v-if="row.estDailyOrders">日估 {{ row.estDailyOrders }} 单 · </span>
              {{ row.contact || '负责人' }} <b>{{ row.phone }}</b>
            </div>
            <div class="m-card-tags">
              <button type="button" class="urgency-pill" :data-level="row.urgencyLevel || 'B'" @click="openUrgencyDrawer(row)">
                {{ row.urgencyLevel || 'B' }}级痛点 ›
              </button>
              <el-tag v-if="row.dishonestCount > 0" type="danger" size="small">🔴 失信预警</el-tag>
              <el-tag v-if="row.visitDaysLeft" type="success" size="small">📍 延保剩{{ row.visitDaysLeft }}天</el-tag>
              <el-tag v-else :type="claimTagType(row)" size="small">{{ CLAIM_LABELS[row.claimStatus] || row.claimStatus }}</el-tag>
            </div>
            <div class="m-card-actions">
              <el-button
                v-if="canEdit && ['UNCLAIMED', 'FEED_WH'].includes(row.claimStatus)"
                type="primary"
                size="small"
                @click="handleClaim(row)"
              >
                认领电销
              </el-button>
              <el-button v-if="canEdit" type="success" plain size="small" @click="openCheckinDialog(row)">
                📍500m打卡
              </el-button>
              <el-button size="small" @click="openDetailDrawer(row, 'quote')">360°与比价</el-button>
              <el-button size="small" @click="openSingleAmapNav(row)">🧭导航</el-button>
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

    <!-- 视图 B：广东高德地推战术地图（5km黄金圈 + 大厦多户聚类 + 一键导航与500m打卡） -->
    <div v-else class="map-workspace" data-testid="gd-map-workspace">
      <div class="map-canvas-wrap">
        <div class="map-floating-bar">
          <el-tag type="primary" effect="dark" size="small">🏭 本园基准云仓 · 5km(15min车程)黄金地推圈</el-tag>
          <el-tag type="warning" size="small">🏢 紫标 = 同栋大厦聚集多户</el-tag>
          <el-button size="small" type="success" @click="routePreviewOn = !routePreviewOn">
            {{ routePreviewOn ? '隐藏顺路拜访线' : '🚗 预览最优顺路串联线' }}
          </el-button>
        </div>

        <!-- 若已配置高德 Web JS Key 则挂载真实高德瓦片容器，否则展示高精 GCJ-02 广东产业带坐标战术图 -->
        <div v-if="connectors.amapJsConfigured" id="gd-amap-real-canvas" class="amap-real-canvas"></div>
        <div v-else class="tactical-map-stage">
          <div class="wh-golden-circle">
            <span class="circle-caption">⭕ 云仓 5km 黄金揽收圈（短驳最低成本区）</span>
          </div>

          <svg v-if="routePreviewOn && mapPins.length >= 2" class="route-svg" viewBox="0 0 100 100" preserveAspectRatio="none">
            <polyline
              :points="routePolylinePoints"
              fill="none"
              stroke="#2563eb"
              stroke-width="0.9"
              stroke-dasharray="2 1.5"
            />
          </svg>

          <!-- 园区中心云仓锚点 -->
          <div class="map-pin wh-pin" style="left: 48%; top: 50%">
            <span>🏭 广东园区主力云仓</span>
          </div>

          <!-- 公海客户坐标锚点 -->
          <button
            v-for="pin in mapPins"
            :key="pin.row.customerId"
            type="button"
            class="map-pin cust-pin"
            :class="{
              active: activeMapRow?.customerId === pin.row.customerId,
              cluster: pin.row.buildingPeerCount >= 2,
              hot: ['CALC_HOT', 'FEIYU_AD'].includes(pin.row.seaSource)
            }"
            :style="{ left: `${pin.x}%`, top: `${pin.y}%` }"
            @click="activeMapCustomerId = pin.row.customerId"
          >
            <span v-if="pin.row.buildingPeerCount >= 2">🏢 {{ pin.row.buildingName }}({{ pin.row.buildingPeerCount }}户)</span>
            <span v-else>📍 {{ shortName(pin.row.name) }}</span>
          </button>

          <div class="map-honest-foot">
            示意坐标图（非真实比例）；在「外部数据连接器中心」开通高德后显示真实街道地图
          </div>
        </div>
      </div>

      <!-- 地图右侧选中点位作战卡 -->
      <aside class="map-side-card">
        <template v-if="activeMapRow">
          <div class="side-card-head">
            <el-tag size="small" type="primary">{{ ZONE_NAMES[activeMapRow.zoneCode] || '广东战区' }}</el-tag>
            <el-tag v-if="activeMapRow.dishonestCount > 0" size="small" type="danger">🔴 失信禁月结</el-tag>
            <el-tag v-else size="small" type="success">🟢 信用正常</el-tag>
          </div>
          <h3 class="side-cust-name">{{ activeMapRow.name }}</h3>
          <div class="sub-note">📍 {{ activeMapRow.address || '地址待扫楼补录' }}</div>
          <div v-if="activeMapRow.distNearestWhKm != null" class="sub-note" style="margin-top: 4px">
            🚗 距 <b>{{ activeMapRow.nearestWhName || '本园云仓' }}</b> 约 <b>{{ activeMapRow.distNearestWhKm }} km</b>
            <span v-if="activeMapRow.buildingPeerCount >= 2"> · 同楼共 {{ activeMapRow.buildingPeerCount }} 户可连访</span>
          </div>

          <div class="side-info-box">
            <div><b>主营与单量</b>：{{ activeMapRow.category || '综合电商' }} · 日估 {{ activeMapRow.estDailyOrders || '待核' }} 单</div>
            <div><b>联系人电话</b>：{{ activeMapRow.contact || '负责人' }} · <b>{{ activeMapRow.phone }}</b></div>
            <div><b>名下网店</b>：{{ (activeMapRow.shops || []).map(s => `${s.platform}:${s.shopName}`).join(' / ') || '待穿透' }}</div>
            <div><b>痛点备忘</b>：{{ activeMapRow.blockNote || '适合上门当面算打包人工替代账' }}</div>
          </div>

          <div class="side-btn-stack">
            <el-button type="primary" @click="openSingleAmapNav(activeMapRow)">🧭 唤起高德驾车导航去现场</el-button>
            <el-button v-if="canEdit" type="success" @click="openCheckinDialog(activeMapRow)">
              📸 现场 ≤{{ summaryData.checkinMaxMeters || 500 }}m 拍照打卡（解锁{{ summaryData.visitProtectDays || 15 }}天保护）
            </el-button>
            <el-button @click="openDetailDrawer(activeMapRow, 'quote')">📊 打开 360° 全景与多仓比价单</el-button>
          </div>
        </template>
        <el-empty v-else description="当前筛选无落图客户，请切换广东分区或执行扫楼雷达" />
      </aside>
    </div>

    <!-- 三层下钻抽屉（L1 核心结论 -> L2 判据列表 -> L3 五联物证） -->
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
          <div class="sub-note">{{ urgencyDrawer.row.urgencyReason || '广东公海标准储备线索，待首通核实' }}</div>
        </div>
        <div class="drill-box">
          <span class="layer-chip">L3 原始物证快照</span>
          <div class="sub-note" style="margin-top: 6px">
            <code>zone={{ urgencyDrawer.row.zoneCode || 'GZ' }}, seaSource={{ urgencyDrawer.row.seaSource }}, category={{ urgencyDrawer.row.category || '—' }}, estDailyOrders={{ urgencyDrawer.row.estDailyOrders ?? '—' }}, logisticsScore={{ urgencyDrawer.row.logisticsScore ?? '—' }}</code>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 客户 360° 全景档案（4页签：工商与失信 / 店铺矩阵 / 高德与打卡足迹 / 多仓价卡比价&首通纪要） -->
    <el-drawer v-model="detailDrawer.visible" :title="`客户 360° 作战全景 · ${detailDrawer.row?.name || ''}`" size="min(680px, 95vw)" append-to-body>
      <div v-if="detailDrawer.row" class="drill-body">
        <el-tabs v-model="detailDrawer.tab">
          <el-tab-pane label="🏢 工商与失信体检" name="qcc">
            <el-alert
              v-if="detailDrawer.row.dishonestCount > 0"
              type="error"
              :closable="false"
              show-icon
              style="margin-bottom: 12px"
              :title="`高危风控：该主体命中 ${detailDrawer.row.dishonestCount} 条失信/限高记录，签约仅允许预充值扣费，严禁开放月结账期！`"
            />
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="企业全称" :span="2">{{ detailDrawer.row.name }}</el-descriptions-item>
              <el-descriptions-item label="统一信用代码">{{ detailDrawer.row.creditCode || '待企查对齐' }}</el-descriptions-item>
              <el-descriptions-item label="法定代表人">{{ detailDrawer.row.legalPerson || '待核实' }}</el-descriptions-item>
              <el-descriptions-item label="注册资本">{{ detailDrawer.row.regCapital || '待核实' }}</el-descriptions-item>
              <el-descriptions-item label="社保参保人数">{{ detailDrawer.row.insuredCount ?? '—' }} 人</el-descriptions-item>
              <el-descriptions-item label="广东战区分区">{{ ZONE_NAMES[detailDrawer.row.zoneCode] || detailDrawer.row.zoneCode || '广州战区' }}</el-descriptions-item>
              <el-descriptions-item label="联系方式">{{ detailDrawer.row.contact || '负责人' }} · {{ detailDrawer.row.phone }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="canEdit" style="margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap">
              <el-button size="small" type="primary" plain @click="handlePrivacyCall(detailDrawer.row)">📞 阿里云 AXB 隐私小号一键外呼</el-button>
              <el-button size="small" @click="router.push('/crm/marketing/setting?tab=connectors')">🔌 查看企查查/天眼查/探迹连接器</el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane :label="`🛍️ 店铺矩阵 (${(detailDrawer.row.shops || []).length})`" name="shops">
            <div class="drill-box" style="margin-bottom: 10px">
              <div class="drill-box-head">
                <b>📲 10 秒官方扫码验店（抖店 / 淘宝1688 / 拼多多）</b>
                <el-tag size="small" type="success">防口头虚报日单量</el-tag>
              </div>
              <div class="sub-note" style="margin: 4px 0">生成一次性加密验店函发给货主，点亮官方开放平台 Key 后自动回写近 30 天真实支付单量与退货率：</div>
              <div v-if="canEdit" style="display: flex; gap: 6px; flex-wrap: wrap; margin-top: 6px">
                <el-button size="small" type="danger" plain @click="createShopInvite('DOUYIN')">生成抖店验店函</el-button>
                <el-button size="small" type="warning" plain @click="createShopInvite('TAOBAO')">生成淘宝/1688验店函</el-button>
                <el-button size="small" type="primary" plain @click="createShopInvite('PDD')">生成拼多多验店函</el-button>
              </div>
              <div v-if="detailDrawer.inviteResult" class="sub-note" style="margin-top: 8px; color: #166534">
                ✅ 验店链接已生成并复制：<code>{{ detailDrawer.inviteResult.invitePath }}</code>（{{ detailDrawer.inviteResult.modeHint }}）
              </div>
            </div>

            <div v-if="detailDrawer.verifications.length" style="margin-bottom: 10px">
              <div v-for="v in detailDrawer.verifications" :key="v.id" class="drill-box" style="margin-bottom: 6px">
                <b>[{{ v.platform }}] 近30天单量：{{ v.verified30dOrders ?? '等待货主填写' }} 单</b>
                <el-tag size="small" :type="v.status === 'OAUTH_VERIFIED' ? 'success' : 'warning'" style="margin-left: 6px">
                  {{ v.status === 'OAUTH_VERIFIED' ? '官方核验' : v.status === 'SELF_REPORTED' ? '货主自报（未核验）' : '待填写' }}
                </el-tag>
                <span v-if="v.verifiedDailyAvg">（折合日均 {{ v.verifiedDailyAvg }} 单）</span>
              </div>
            </div>

            <div v-if="(detailDrawer.row.shops || []).length" class="drill-body">
              <div v-for="(s, idx) in detailDrawer.row.shops" :key="idx" class="drill-box">
                <div class="drill-box-head">
                  <b>{{ s.platform }} · {{ s.shopName }}</b>
                  <el-tag v-if="s.rankNo" size="small" type="warning">类目榜 #{{ s.rankNo }}</el-tag>
                </div>
                <div class="sub-note" style="margin-top: 4px">
                  <span v-if="s.monthlySales">近30天销量约 {{ s.monthlySales }} 单 · </span>
                  <span v-if="s.logisticsScore">物流体验分 {{ s.logisticsScore }}</span>
                  <span v-else>同一营业执照关联电商店铺</span>
                </div>
              </div>
            </div>
            <el-empty v-else description="当前主体暂无关联线上店铺明细" />
          </el-tab-pane>

          <el-tab-pane label="📍 高德位置与打卡足迹" name="checkin">
            <div class="drill-box" style="margin-bottom: 12px">
              <div><b>标准详细地址</b>：{{ detailDrawer.row.address || '待补充详细门牌' }}</div>
              <div class="sub-note" style="margin-top: 4px">
                GCJ-02 坐标：{{ detailDrawer.row.lng || '—' }}, {{ detailDrawer.row.lat || '—' }}
                <span v-if="detailDrawer.row.distNearestWhKm != null"> · 距{{ detailDrawer.row.nearestWhName || '本园云仓' }} {{ detailDrawer.row.distNearestWhKm }}km</span>
              </div>
              <div style="margin-top: 8px; display: flex; gap: 8px; flex-wrap: wrap">
                <el-button size="small" type="primary" plain @click="openSingleAmapNav(detailDrawer.row)">🧭 高德导航去现场</el-button>
                <el-button v-if="canEdit" size="small" type="success" @click="openCheckinDialog(detailDrawer.row)">📸 立即提交 500m 现场打卡</el-button>
              </div>
            </div>

            <div class="row-title" style="margin-bottom: 8px">历史现场 GPS 实拍打卡留痕</div>
            <div v-if="detailDrawer.checkins.length" class="drill-body">
              <div v-for="ck in detailDrawer.checkins" :key="ck.id" class="drill-box">
                <div class="drill-box-head">
                  <b>{{ ck.operator }} · 距客户坐标 {{ ck.distanceMeters }}m</b>
                  <el-tag type="success" size="small">延保至 {{ String(ck.protectedUntil || '').slice(0, 10) }}</el-tag>
                </div>
                <div class="sub-note" style="margin-top: 4px">纪要：{{ ck.visitNote }}</div>
                <div class="sub-note">实拍凭证：<code>{{ ck.photoUrl }}</code> · {{ ck.createdAt }}</div>
              </div>
            </div>
            <div v-else class="sub-note">暂无现场 GPS 打卡记录（到达客户门头 ≤500m 拍照打卡可解锁 15 天深度保护期）。</div>
          </el-tab-pane>

          <el-tab-pane label="💰 多仓比价 & 首通纪要" name="quote">
            <CustomerQuotePanel
              v-if="detailDrawer.visible && effectiveProjectId"
              :key="`${effectiveProjectId}/${detailDrawer.row.customerId}`"
              :customer-id="detailDrawer.row.customerId"
              :project-id="effectiveProjectId"
            />
            <el-form v-if="canEdit" label-position="top" style="margin-top: 12px">
              <el-form-item label="核实后主营类目">
                <el-input v-model="detailDrawer.category" maxlength="64" placeholder="如：个人洗护 / 女装服饰 / 数码小家电" />
              </el-form-item>
              <el-form-item label="核实日均发货单量（单/天）">
                <el-input-number v-model="detailDrawer.estDailyOrders" :min="0" :max="200000" style="width: 100%" />
              </el-form-item>
              <el-form-item label="电销首通核实纪要（保存即解除 72h 超时自动弹回）" required>
                <el-input
                  v-model="detailDrawer.note"
                  type="textarea"
                  :rows="3"
                  maxlength="500"
                  show-word-limit
                  placeholder="记录实际日单量、件重、当前发货痛点与约看仓时间..."
                />
              </el-form-item>
              <div style="display: flex; gap: 8px; justify-content: flex-end">
                <el-button @click="detailDrawer.visible = false">关闭</el-button>
                <el-button type="primary" :loading="detailDrawer.busy" @click="submitFirstTouch">保存首通核实纪要</el-button>
              </div>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <!-- 弹窗 1：类目 TOP500 店铺榜自动企查对齐 + 广东定向省钱锁洗数入池 -->
    <el-dialog v-model="washDialog.visible" title="🏆 类目 TOP500 店铺榜自动对齐 & 三道防撞洗数" width="min(650px, 95vw)">
      <el-alert
        type="success"
        :closable="false"
        show-icon
        title="省钱与护客双流水线：① 外省主体自动跳过免扣API费 → ② 命中30天本地工商缓存0扣费 → ③ 护90天私海、护云仓HMAC老客、剔代账重号。"
      />
      <el-form label-position="top" style="margin-top: 14px">
        <el-form-item label="榜单/名单来源渠道">
          <el-select v-model="washDialog.seaSource" style="width: 100%">
            <el-option value="CHANMAMA" label="🏆 蝉妈妈/考古加/电霸 类目TOP500店铺榜（支持无手机号自动企查对齐）" />
            <el-option value="TUNGEE_JOB" label="📦 探迹/BOSS直聘“正在招打包工”广东企业表" />
            <el-option value="PARK_CRM" label="🏢 广州美博会/展会名录 & 园区调剂名单" />
            <el-option value="MANUAL_IMPORT" label="📋 其他广东产业带拓客名单" />
          </el-select>
        </el-form-item>

        <el-radio-group v-model="washDialog.mode" style="margin-bottom: 12px">
          <el-radio-button value="paste">快捷粘贴榜单/名单</el-radio-button>
          <el-radio-button value="file">上传 Excel / CSV 表格</el-radio-button>
        </el-radio-group>

        <el-form-item v-if="washDialog.mode === 'file'" label="选择表格文件（支持表头：店铺名、公司名、平台、手机号、类目、月销/日均单量、物流分、地址/地区）">
          <input type="file" accept=".xlsx,.xls,.csv" @change="onPickWashFile" />
        </el-form-item>
        <el-form-item v-else label="每行一条：企业或店铺名,手机号(可留空),类目,日均单量,物流分,广东地址">
          <el-input
            v-model="washDialog.pasteText"
            type="textarea"
            :rows="5"
            placeholder="广州白云悦颜生物科技有限公司,13826019920,个人洗护,1350,4.43,广州市白云区嘉禾望岗美妆电商大厦9楼&#10;杭州四季青某服饰店,13957102211,女装服饰,900,4.51,浙江省杭州市上城区"
          />
        </el-form-item>
      </el-form>

      <div v-if="washDialog.preview" class="wash-result-box">
        <div><b>洗数与省钱对账结果（总计 {{ washDialog.preview.totalRows }} 行）</b>：</div>
        <div class="wash-stats-grid">
          <div>💰 外省锁自动跳过省钱：<b>{{ washDialog.preview.skippedOutProvince || 0 }}</b> 行</div>
          <div>⚡ 命中30天本地缓存：<b>{{ washDialog.preview.qccCacheHits || 0 }}</b> 次</div>
          <div>🛡️ 护在锁私海拦截：<b>{{ washDialog.preview.blockedPrivateLock }}</b> 行</div>
          <div>🔐 护云仓 HMAC 老客：<b>{{ washDialog.preview.blockedWhHmac }}</b> 行</div>
          <div>🧹 剔除代账重号/无号：<b>{{ washDialog.preview.blockedDupAgency }}</b> 行</div>
          <div class="ok-text">✅ 广东净增可入公海：<b>{{ washDialog.preview.insertedRows }}</b> 户</div>
        </div>
      </div>

      <template #footer>
        <el-button :disabled="washDialog.busy" @click="washDialog.visible = false">取消</el-button>
        <el-button :loading="washDialog.busy" @click="submitWash(true)">先预检省钱与防撞结果</el-button>
        <el-button type="primary" :loading="washDialog.busy" @click="submitWash(false)">确认洗数入池</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 2：单店名称反查背后广东公司并入池 -->
    <el-dialog v-model="shopDialog.visible" title="🛍️ 输入网店名 → 反查广东工商主体与地址入池" width="min(520px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="所属电商平台">
          <el-select v-model="shopDialog.platform" style="width: 100%">
            <el-option value="抖音" label="抖音小店" />
            <el-option value="天猫" label="天猫 / 淘宝皇冠店" />
            <el-option value="拼多多" label="拼多多旗舰店" />
            <el-option value="1688" label="1688 广东源头厂店" />
          </el-select>
        </el-form-item>
        <el-form-item label="网店名称或经营主体关键词" required>
          <el-input v-model="shopDialog.shopName" placeholder="例如：广州悦颜洗护旗舰店" clearable />
        </el-form-item>
        <el-form-item label="主营类目">
          <el-input v-model="shopDialog.category" placeholder="如：个人洗护 / 女装服饰 / 休闲食品" />
        </el-form-item>
        <el-checkbox v-model="shopDialog.forceUnlockRegion">特批解锁外省主体（默认仅对齐广东省内主体）</el-checkbox>
      </el-form>
      <template #footer>
        <el-button :disabled="shopDialog.busy" @click="shopDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="shopDialog.busy" @click="submitShopLookup">穿透防撞并入池</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 3：高德周边 10km 广东电商园/商圈 POI 扫楼雷达 -->
    <el-dialog v-model="radarDialog.visible" title="📡 高德周边 10km 广东商圈 POI 扫楼雷达（0元免费配额）" width="min(540px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="选择广东核心电商产业带中心锚点">
          <el-select v-model="radarDialog.hubKey" style="width: 100%" @change="onPickRadarHub">
            <el-option v-for="h in GD_RADAR_HUBS" :key="h.key" :value="h.key" :label="h.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="扫描半径（米）">
          <el-input-number v-model="radarDialog.radiusMeters" :min="1000" :max="30000" :step="1000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="高德 POI 行业关键词（竖线分隔）">
          <el-input v-model="radarDialog.keywords" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="radarDialog.busy" @click="radarDialog.visible = false">取消</el-button>
        <el-button type="warning" :loading="radarDialog.busy" @click="submitAmapRadar">启动周边扫楼入池</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 4：外勤现场 500m GPS 定位 + 现场拍照打卡（解锁 15 天保护期） -->
    <el-dialog v-model="checkinDialog.visible" title="📸 外勤现场打卡（500 米内 + 现场拍照）" width="min(540px, 95vw)">
      <div v-if="checkinDialog.row">
        <el-alert
          v-if="!checkinDialog.row.lng || !checkinDialog.row.lat"
          type="warning"
          :closable="false"
          show-icon
          title="该客户还没有落图坐标，无法校验距离，暂不能打卡。请先补全详细地址，并在连接器中心开通高德完成落图。"
          style="margin-bottom: 10px"
        />
        <div class="drill-box" style="margin-bottom: 12px">
          <div><b>拜访目标</b>：{{ checkinDialog.row.name }}</div>
          <div class="sub-note">目标地址：{{ checkinDialog.row.address || '地址待补全' }}</div>
          <div style="margin-top: 8px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
            <el-button size="small" type="primary" plain data-testid="btn-locate-gps" @click="locateBrowserGps">📍 获取当前定位</el-button>
            <template v-if="isDevBuild && checkinDialog.row.lng && checkinDialog.row.lat">
              <el-button size="small" data-testid="btn-mock-near-gps" @click="useMockFieldGps(true)">[开发环境] 模拟到门头</el-button>
              <el-button size="small" data-testid="btn-mock-far-gps" @click="useMockFieldGps(false)">[开发环境] 模拟超距</el-button>
            </template>
          </div>
          <div class="sub-note" style="margin-top: 6px">
            当前定位：{{ checkinDialog.lng && checkinDialog.lat ? `${checkinDialog.lng}, ${checkinDialog.lat}` : '尚未获取（只能通过定位按钮获取，不能手填）' }}
          </div>
          <div v-if="checkinPreviewMeters != null" style="margin-top: 8px">
            <el-tag :type="checkinPreviewMeters <= (summaryData.checkinMaxMeters || 500) ? 'success' : 'danger'">
              距客户门头约 {{ checkinPreviewMeters }} 米（要求 ≤ {{ summaryData.checkinMaxMeters || 500 }} 米）
            </el-tag>
          </div>
        </div>

        <el-form label-position="top">
          <el-form-item label="现场照片（门头或待发包裹，须现场拍摄）" required>
            <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap">
              <PhotoCapture :disabled="checkinDialog.uploading" @capture="onCheckinPhoto" />
              <span v-if="checkinDialog.uploading" class="sub-note">照片上传中…</span>
              <el-tag v-else-if="checkinDialog.photoFileId" type="success" data-testid="checkin-photo-ok">✔ 照片已上传</el-tag>
              <span v-else class="sub-note">请在 30 分钟内现场拍摄后提交</span>
            </div>
          </el-form-item>
          <el-form-item label="拜访纪要（至少 4 个字）" required>
            <el-input
              v-model="checkinDialog.note"
              type="textarea"
              :rows="3"
              maxlength="400"
              placeholder="如：现场面见林老板，核实门口日发约1400单，约定周四看番禺一号仓"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button :disabled="checkinDialog.busy" @click="checkinDialog.visible = false">取消</el-button>
        <el-button
          type="success"
          :loading="checkinDialog.busy"
          :disabled="!canSubmitCheckin"
          data-testid="btn-submit-checkin"
          @click="submitCheckin"
        >
          提交打卡并解锁 {{ summaryData.visitProtectDays || 15 }} 天保护期
        </el-button>
      </template>
    </el-dialog>

    <!-- 定向投喂加盟云仓限时认领池弹窗（支持单户与批量） -->
    <el-dialog v-model="feedDialog.visible" title="🏭 定向投喂至加盟云仓限时认领池" width="min(520px, 94vw)">
      <p class="sub-note" style="margin-top: 0">
        将
        <b v-if="feedDialog.batchIds.length">所勾选的 {{ feedDialog.batchIds.length }} 户广东货主</b>
        <b v-else>「{{ feedDialog.row?.name }}」</b>
        开放给已上线加盟云仓主账号限时认领（认领前隐藏电话，认领后 {{ summaryData.slaHours || 72 }}h 内须回填接洽结果，成单免 70% 外部佣金）：
      </p>
      <el-form label-position="top">
        <el-form-item label="限定可认领云仓（留空表示开放给本园区全部已上线云仓）">
          <el-select v-model="feedDialog.warehouseIds" multiple filterable clearable placeholder="全部本园区已上线加盟云仓" style="width: 100%">
            <el-option v-for="w in onlineWarehouses" :key="w.id" :value="w.id" :label="w.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="给云仓的货盘与卡点提示">
          <el-input v-model="feedDialog.note" type="textarea" :rows="3" maxlength="400" placeholder="如：日均 1200 单个人洗护，需气泡柱防漏打包与下周试发..." />
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

    <!-- 弹窗 5：蝉妈妈 / 店透巢 / 探迹 商业 API 在线拉取广东痛点榜单 -->
    <el-dialog v-model="pullDialog.visible" title="🤖 蝉妈妈 / 店透巢 / 探迹 在线拉取广东痛点货主" width="min(560px, 95vw)">
      <el-form label-position="top">
        <el-form-item label="选择商业数据源通道">
          <el-select v-model="pullDialog.connectorCode" style="width: 100%">
            <el-option value="CHANMAMA_API" label="🏆 蝉妈妈 API（抖音广东发货低物流分爆单榜）" />
            <el-option value="DIANTOU_API" label="🛍️ 店透巢 / 电霸 API（天猫/淘宝/拼多多/1688广东货盘）" />
            <el-option value="TUNGEE_API" label="📦 探迹拓客 OpenAPI（广东新发“招打包工/仓管”企业）" />
          </el-select>
        </el-form-item>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px">
          <el-form-item label="目标广东战区">
            <el-select v-model="pullDialog.zoneCode" style="width: 100%">
              <el-option v-for="z in GD_ZONES" :key="z.code" :value="z.code" :label="z.label" />
            </el-select>
          </el-form-item>
          <el-form-item label="主营类目">
            <el-input v-model="pullDialog.category" placeholder="如：个人洗护 / 女装服饰" />
          </el-form-item>
        </div>
      </el-form>
      <div v-if="pullDialog.result" class="drill-box" style="margin-top: 8px">
        <div><b>{{ pullDialog.result.message || `已拉取 ${pullDialog.result.pulledRows || 0} 条，防撞净入 ${pullDialog.result.insertedRows || 0} 户` }}</b></div>
        <div v-if="pullDialog.result.applyGuide" class="sub-note" style="margin-top: 6px">
          📋 <b>派给同事的申领步骤</b>：{{ pullDialog.result.applyGuide }}（官网：{{ pullDialog.result.applyUrl }}）
        </div>
      </div>
      <template #footer>
        <el-button @click="pullDialog.visible = false">关闭</el-button>
        <el-button @click="router.push('/crm/marketing/setting?tab=connectors')">去连接器中心填 Key</el-button>
        <el-button type="primary" :loading="pullDialog.busy" @click="submitPullLeads">执行在线拉取</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { hasPermission } from '@/utils/permission'
import { useProjectStore } from '@/stores/project'
import { mktPublicSeaApi, mktWarehouseApi, mktConnectorHubApi } from '@/api/marketing'
import { fileApi } from '@/api/file'
import PhotoCapture from '@/components/PhotoCapture.vue'
import CustomerQuotePanel from './CustomerQuotePanel.vue'
import { buildPublicSeaDrilldownCards } from './publicSeaDrilldown'

const router = useRouter()
const projectStore = useProjectStore()
const canEdit = computed(() => hasPermission('crm:marketing:public-sea:edit') || hasPermission('crm:marketing:customer:edit'))
const effectiveProjectId = computed(() => projectStore.currentProjectId || null)

const GD_ZONES = [
  { code: 'ALL_GD', label: '广东全境' },
  { code: 'GZ', label: '广州·白云美妆/番禺女装' },
  { code: 'DG', label: '东莞·虎门鞋服/大朗毛织' },
  { code: 'FS', label: '佛山·顺德小家电/南海内衣' },
  { code: 'SZ', label: '深圳·龙岗数码跨境' },
  { code: 'YD', label: '粤东·普宁内衣/澄海玩具' },
  { code: 'UNKNOWN', label: '待定位（缺地址）' }
]
const ZONE_NAMES = {
  GZ: '广州战区',
  DG: '东莞战区',
  FS: '佛山战区',
  SZ: '深圳战区',
  YD: '粤东战区',
  OTHER_GD: '广东其他战区',
  OUT_GD: '外省主体',
  UNKNOWN: '待定位'
}
const GD_RADAR_HUBS = [
  { key: 'gz_by', label: '广州白云 · 嘉禾望岗/均禾美妆直播带', lng: 113.28561, lat: 23.22481, kw: '化妆品有限公司|美妆直播基地|电子商务园' },
  { key: 'gz_py', label: '广州番禺 · 南村万博/大石女装电商带', lng: 113.34862, lat: 23.02854, kw: '服饰有限公司|女装直播基地|服装电商大厦' },
  { key: 'dg_hm', label: '东莞虎门 · 博涌服装城/鞋服供应链带', lng: 113.67312, lat: 22.82195, kw: '服装电子商务|鞋服供应链|电商产业园' },
  { key: 'fs_sd', label: '佛山顺德 · 北滘小家电/南海内衣带', lng: 113.21684, lat: 22.93218, kw: '电器电子商务|小家电有限公司|内衣电商园' },
  { key: 'sz_lg', label: '深圳龙岗 · 坂田/华南城3C数码电商带', lng: 114.06521, lat: 22.65842, kw: '数码电子商务|科技供应链|跨境电商园' }
]

const SOURCE_LABELS = {
  CALC_HOT: '🔥 30秒算价留资',
  FEIYU_AD: '📣 巨量飞鱼Webhook',
  AMAP_POI: '📡 高德POI扫楼',
  CHANMAMA: '🏆 榜单企查对齐',
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

const viewMode = ref('list')
const routePreviewOn = ref(false)
const activeMapCustomerId = ref(null)
const selectedRows = ref([])
const summaryData = ref({})
const list = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const onlineWarehouses = ref([])

const connectors = computed(() => summaryData.value?.connectors || {})
const showConnectorHint = computed(() => {
  const c = connectors.value
  return c && Object.keys(c).length > 0 && (!c.amapWebConfigured || !c.qccConfigured)
})

const query = reactive({
  zoneCode: 'ALL_GD',
  seaSource: 'ALL',
  claimStatus: 'ALL',
  distFilter: 'ALL',
  platform: 'ALL',
  riskFilter: 'ALL',
  keyword: '',
  pageNo: 1,
  pageSize: 20
})

function zoneCountText(code) {
  const zc = summaryData.value?.zoneCounts
  if (!zc || typeof zc !== 'object') return ''
  if (code === 'ALL_GD') {
    const sum = Object.entries(zc).reduce((acc, [k, v]) => (k === 'OUT_GD' ? acc : acc + Number(v || 0)), 0)
    return sum > 0 ? String(sum) : ''
  }
  const n = Number(zc[code] || 0)
  return n > 0 ? String(n) : ''
}

function selectZone(code) {
  query.zoneCode = code
  search()
}

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
  if (ev.switchMap5km) {
    query.distFilter = '5KM'
    viewMode.value = 'map'
    search()
    return
  }
  if (ev.filterDist) { query.distFilter = ev.filterDist; search(); return }
  if (ev.filterRisk) { query.riskFilter = ev.filterRisk; search(); return }
  if (ev.filterSource) { query.seaSource = ev.filterSource; query.claimStatus = 'ALL'; search(); return }
  if (ev.filterClaimStatus) { query.claimStatus = ev.filterClaimStatus; query.seaSource = 'ALL'; search() }
}

function claimTagType(row) {
  if (row.claimStatus === 'UNCLAIMED') return 'primary'
  if (!row.firstTouchedAt && row.hoursLeft != null && row.hoursLeft <= 12) return 'warning'
  return 'success'
}

// 地图锚点投影（将广东经纬度映射为战术地图画布百分比坐标；无经纬度时按客户ID确定性分布）
const mapPins = computed(() => {
  const rows = list.value
  if (!rows.length) return []
  return rows.slice(0, 30).map((row, idx) => {
    const lng = Number(row.lng)
    const lat = Number(row.lat)
    let x = 22 + ((idx * 19) % 58)
    let y = 24 + ((idx * 23) % 54)
    if (Number.isFinite(lng) && Number.isFinite(lat) && lng > 108 && lat > 20) {
      // 以广州/珠三角轴心 (113.35, 23.03) 做比例缩放并钳位在可视画布内
      x = Math.max(14, Math.min(86, Math.round(48 + (lng - 113.35) * 42)))
      y = Math.max(16, Math.min(84, Math.round(50 - (lat - 23.03) * 58)))
    }
    return { row, x, y }
  })
})

const activeMapRow = computed(() => {
  if (!list.value.length) return null
  return list.value.find(r => r.customerId === activeMapCustomerId.value) || list.value[0]
})

const routePolylinePoints = computed(() => {
  const pts = [{ x: 48, y: 50 }, ...mapPins.value.slice(0, 5)]
  return pts.map(p => `${p.x},${p.y}`).join(' ')
})

function shortName(s) {
  const t = String(s || '').replace(/^(广东省|广州市|东莞市|深圳市|佛山市|揭阳市)/, '')
  return t.length > 8 ? `${t.slice(0, 8)}…` : t
}

function switchToMapView() {
  viewMode.value = 'map'
  if (!activeMapCustomerId.value && list.value.length) {
    activeMapCustomerId.value = list.value[0].customerId
  }
  if (connectors.value.amapJsConfigured && connectors.value.amapJsKey) {
    nextTick(() => mountRealAmapIfConfigured())
  }
}

function focusRowOnMap(row) {
  activeMapCustomerId.value = row.customerId
  switchToMapView()
}

function mountRealAmapIfConfigured() {
  const key = connectors.value.amapJsKey
  const el = document.getElementById('gd-amap-real-canvas')
  if (!key || !el || typeof window === 'undefined') return
  const render = () => {
    if (!window.AMap) return
    const center = activeMapRow.value?.lng && activeMapRow.value?.lat
      ? [Number(activeMapRow.value.lng), Number(activeMapRow.value.lat)]
      : [113.34862, 23.02854]
    const map = new window.AMap.Map(el, { zoom: 12, center })
    list.value.forEach(r => {
      if (r.lng && r.lat) {
        const marker = new window.AMap.Marker({
          position: [Number(r.lng), Number(r.lat)],
          title: r.name
        })
        marker.on('click', () => { activeMapCustomerId.value = r.customerId })
        map.add(marker)
      }
    })
  }
  if (window.AMap) { render(); return }
  if (!document.getElementById('amap-js-sdk')) {
    const sc = document.createElement('script')
    sc.id = 'amap-js-sdk'
    sc.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}`
    sc.onload = render
    document.head.appendChild(sc)
  }
}

function onSelectionChange(rows) {
  selectedRows.value = rows || []
}

function buildAmapSingleUri(row) {
  const name = encodeURIComponent(row.address || row.name || '广东目标客户')
  if (row.lng && row.lat) {
    return `https://uri.amap.com/marker?position=${row.lng},${row.lat}&name=${name}&callnative=1`
  }
  return `https://uri.amap.com/search?keyword=${name}&city=${encodeURIComponent(row.city || '广州')}&callnative=1`
}

function openSingleAmapNav(row) {
  const uri = buildAmapSingleUri(row)
  ElMessage.success(`已生成「${row.name}」高德导航指令：${row.address || row.city || '广东战区'}`)
  if (typeof window !== 'undefined' && window.navigator?.clipboard?.writeText) {
    window.navigator.clipboard.writeText(uri).catch(() => {})
  }
}

function openAmapBatchRoute() {
  const rows = selectedRows.value
  if (!rows.length) return
  const dest = rows[rows.length - 1]
  const vias = rows.slice(0, -1).filter(r => r.lng && r.lat).slice(0, 3)
  let uri = buildAmapSingleUri(dest)
  if (dest.lng && dest.lat) {
    const viaParam = vias.length
      ? `&via=${vias.map(v => `${v.lng},${v.lat},${encodeURIComponent(shortName(v.name))}`).join('|')}`
      : ''
    uri = `https://uri.amap.com/navigation?to=${dest.lng},${dest.lat},${encodeURIComponent(shortName(dest.name))}${viaParam}&mode=car&callnative=1`
  }
  if (typeof window !== 'undefined' && window.navigator?.clipboard?.writeText) {
    window.navigator.clipboard.writeText(uri).catch(() => {})
  }
  viewMode.value = 'map'
  routePreviewOn.value = true
  ElMessage.success(`已串联 ${rows.length} 站广东顺路拜访路线并复制高德导航链接`)
}

async function loadSummary() {
  summaryData.value = await mktPublicSeaApi.summary({ projectId: effectiveProjectId.value })
}

async function loadPage() {
  loading.value = true
  try {
    const res = await mktPublicSeaApi.page({
      projectId: effectiveProjectId.value,
      zoneCode: query.zoneCode,
      seaSource: query.seaSource,
      claimStatus: query.claimStatus,
      distFilter: query.distFilter,
      platform: query.platform,
      riskFilter: query.riskFilter,
      keyword: query.keyword || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    })
    list.value = res?.records || []
    total.value = Number(res?.total || 0)
    if (list.value.length && !list.value.some(r => r.customerId === activeMapCustomerId.value)) {
      activeMapCustomerId.value = list.value[0].customerId
    }
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
  query.zoneCode = 'ALL_GD'
  query.seaSource = 'ALL'
  query.claimStatus = 'ALL'
  query.distFilter = 'ALL'
  query.platform = 'ALL'
  query.riskFilter = 'ALL'
  query.keyword = ''
  search()
}

async function handleClaim(row) {
  try {
    const res = await mktPublicSeaApi.claim(row.customerId)
    ElMessage.success(`认领成功，已解锁电话 ${res.phone || ''}，请于 ${res.hoursLeft || 72}h 内首触达或现场500m打卡`)
    await reloadAll()
  } catch (_) { /* request.js 统一处理报错 */ }
}

const urgencyDrawer = reactive({ visible: false, row: null })
function openUrgencyDrawer(row) {
  urgencyDrawer.row = row
  urgencyDrawer.visible = true
}

const detailDrawer = reactive({
  visible: false, tab: 'qcc', row: null,
  category: '', estDailyOrders: 0, note: '', checkins: [],
  verifications: [], inviteResult: null, busy: false
})
async function openDetailDrawer(row, tab = 'qcc') {
  detailDrawer.row = row
  detailDrawer.tab = tab
  detailDrawer.category = row.category || ''
  detailDrawer.estDailyOrders = row.estDailyOrders || 0
  detailDrawer.note = row.firstTouchNote || ''
  detailDrawer.checkins = []
  detailDrawer.verifications = []
  detailDrawer.inviteResult = null
  detailDrawer.visible = true
  if (mktPublicSeaApi.checkins) {
    try {
      detailDrawer.checkins = (await mktPublicSeaApi.checkins(row.customerId)) || []
    } catch (_) { detailDrawer.checkins = [] }
  }
  if (mktConnectorHubApi?.listShopVerifications) {
    try {
      detailDrawer.verifications = (await mktConnectorHubApi.listShopVerifications(row.customerId)) || []
    } catch (_) { detailDrawer.verifications = [] }
  }
}

async function createShopInvite(platform) {
  if (!detailDrawer.row || !mktConnectorHubApi?.createShopVerifyInvite) return
  try {
    const res = await mktConnectorHubApi.createShopVerifyInvite(detailDrawer.row.customerId, {
      platform,
      shopName: detailDrawer.row.shopName || detailDrawer.row.name
    })
    detailDrawer.inviteResult = res
    const fullUrl = `${typeof window !== 'undefined' ? window.location.origin : ''}${res.invitePath}`
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(fullUrl).catch(() => {})
    }
    ElMessage.success(`已生成 ${platform} 10秒验店邀请链接并复制到剪贴板`)
  } catch (_) { /* request.js统一弹错 */ }
}

async function handlePrivacyCall(row) {
  if (!row || !mktConnectorHubApi?.privacyCall) return
  try {
    const res = await mktConnectorHubApi.privacyCall(row.customerId, {})
    if (res?.status === 'OK') ElMessage.success(res.message)
    else ElMessage.warning(res?.message || '隐私小号待同事申领开通')
  } catch (_) { /* request.js统一弹错 */ }
}

const pullDialog = reactive({
  visible: false, connectorCode: 'CHANMAMA_API', zoneCode: 'GZ',
  category: '个人洗护', result: null, busy: false
})
function openPullLeadsDialog() {
  pullDialog.result = null
  pullDialog.visible = true
}
async function submitPullLeads() {
  if (pullDialog.busy || !mktConnectorHubApi?.pullLeads) return
  pullDialog.busy = true
  try {
    pullDialog.result = await mktConnectorHubApi.pullLeads({
      projectId: effectiveProjectId.value || undefined,
      connectorCode: pullDialog.connectorCode,
      zoneCode: pullDialog.zoneCode,
      category: pullDialog.category
    })
    if (pullDialog.result?.insertedRows > 0) {
      ElMessage.success(`拉取入池成功：新增 ${pullDialog.result.insertedRows} 户广东货主`)
      await reloadAll()
    }
  } catch (_) { /* request.js统一弹错 */ } finally {
    pullDialog.busy = false
  }
}

async function submitFirstTouch() {
  if (!detailDrawer.row || detailDrawer.busy) return
  detailDrawer.busy = true
  try {
    await mktPublicSeaApi.firstTouch(detailDrawer.row.customerId, {
      note: detailDrawer.note,
      category: detailDrawer.category,
      estDailyOrders: detailDrawer.estDailyOrders || undefined
    })
    ElMessage.success('首通核实纪要已保存，72h 超时自动弹回已解除')
    detailDrawer.visible = false
    await reloadAll()
  } catch (_) {
    /* request.js 已统一弹出业务错误提示 */
  } finally {
    detailDrawer.busy = false
  }
}

// 1) 类目 TOP500 店铺榜洗数对齐弹窗
const washDialog = reactive({
  visible: false, mode: 'paste', seaSource: 'CHANMAMA',
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
    .map((line, idx) => {
      const [name, phone, category, daily, score, addr] = line.split(/[，,\t]/).map(s => s?.trim())
      return {
        name,
        shopName: name,
        rankNo: idx + 1,
        phone: phone || undefined,
        category: category || '个人洗护',
        estDailyOrders: daily ? Number(daily) : undefined,
        logisticsScore: score || undefined,
        address: addr || undefined,
        region: addr || undefined
      }
    })
}

async function submitWash(dryRun) {
  if (washDialog.busy) return
  washDialog.busy = true
  try {
    let res
    if (washDialog.mode === 'file') {
      if (!washDialog.file) { ElMessage.warning('请先选择 Excel 或 CSV 榜单文件'); return }
      const fd = new FormData()
      fd.append('file', washDialog.file)
      res = await mktPublicSeaApi.washFile(fd, {
        projectId: effectiveProjectId.value || undefined,
        seaSource: washDialog.seaSource,
        dryRun
      })
    } else {
      const rows = parsePasteRows(washDialog.pasteText)
      if (!rows.length) { ElMessage.warning('请先输入至少 1 行待清洗店铺或企业名单'); return }
      res = await mktPublicSeaApi.washRows({
        projectId: effectiveProjectId.value || undefined,
        seaSource: washDialog.seaSource,
        fileName: '类目TOP500榜单对齐',
        dryRun,
        rows
      })
    }
    washDialog.preview = res
    if (!dryRun) {
      ElMessage.success(`入池完成：广东净入 ${res.insertedRows} 户，外省锁省钱跳过 ${res.skippedOutProvince || 0} 行`)
      washDialog.visible = false
      await reloadAll()
    }
  } catch (_) {
    /* request.js 已统一弹出业务错误提示 */
  } finally {
    washDialog.busy = false
  }
}

// 2) 单店名反查背后广东公司
const shopDialog = reactive({
  visible: false, platform: '抖音', shopName: '', category: '个人洗护', forceUnlockRegion: false, busy: false
})
function openShopLookupDialog() {
  Object.assign(shopDialog, { visible: true, platform: '抖音', shopName: '', category: '个人洗护', forceUnlockRegion: false, busy: false })
}
async function submitShopLookup() {
  if (!shopDialog.shopName.trim()) { ElMessage.warning('请输入要反查的网店名称或企业名称'); return }
  shopDialog.busy = true
  try {
    const res = await mktPublicSeaApi.shopLookup({
      projectId: effectiveProjectId.value || undefined,
      platform: shopDialog.platform,
      shopName: shopDialog.shopName.trim(),
      category: shopDialog.category,
      forceUnlockRegion: shopDialog.forceUnlockRegion
    })
    if (res.skippedOutProvince > 0) {
      ElMessage.warning('识别为外省主体，已被广东定向省钱锁自动拦截免扣费（如需入池可勾选特批解锁）')
    } else if (res.insertedRows > 0) {
      ElMessage.success(`单店穿透成功，已通过防撞闸写入广东公海池`)
      shopDialog.visible = false
      await reloadAll()
    } else {
      ElMessage.warning(res.blockedDetails?.[0]?.reason || '该主体命中防撞保护白名单，未重复入池')
    }
  } catch (_) {
    /* request.js 已统一弹出业务错误提示 */
  } finally {
    shopDialog.busy = false
  }
}

// 3) 高德周边 10km 商圈 POI 扫楼雷达
const radarDialog = reactive({
  visible: false, hubKey: 'gz_by', centerLng: 113.28561, centerLat: 23.22481,
  radiusMeters: 10000, keywords: '化妆品有限公司|美妆直播基地|电子商务园', busy: false
})
function openRadarDialog() {
  onPickRadarHub('gz_by')
  radarDialog.visible = true
}
function onPickRadarHub(k) {
  const h = GD_RADAR_HUBS.find(item => item.key === k) || GD_RADAR_HUBS[0]
  radarDialog.hubKey = h.key
  radarDialog.centerLng = h.lng
  radarDialog.centerLat = h.lat
  radarDialog.keywords = h.kw
}
async function submitAmapRadar() {
  if (radarDialog.busy) return
  radarDialog.busy = true
  try {
    const res = await mktPublicSeaApi.amapRadar({
      projectId: effectiveProjectId.value || undefined,
      centerLng: radarDialog.centerLng,
      centerLat: radarDialog.centerLat,
      radiusMeters: radarDialog.radiusMeters,
      keywords: radarDialog.keywords
    })
    ElMessage.success(`高德周边雷达扫描 ${res.scannedPois || 0} 个 POI，防撞净入公海 ${res.insertedRows || 0} 户`)
    radarDialog.visible = false
    await reloadAll()
  } catch (_) {
    /* request.js 已统一弹出业务错误提示 */
  } finally {
    radarDialog.busy = false
  }
}

// 4) 外勤现场打卡：定位只能通过浏览器获取（不可手填），照片必须现场拍摄上传；开发环境才显示模拟按钮
const isDevBuild = import.meta.env.DEV
const checkinDialog = reactive({
  visible: false, row: null, lng: '', lat: '', photoFileId: null, uploading: false, note: '', busy: false
})
function openCheckinDialog(row) {
  Object.assign(checkinDialog, {
    visible: true, row, lng: '', lat: '', photoFileId: null, uploading: false, note: '', busy: false
  })
}
function useMockFieldGps(within500m) {
  if (!isDevBuild || !checkinDialog.row?.lng || !checkinDialog.row?.lat) return
  const baseLng = Number(checkinDialog.row.lng)
  const baseLat = Number(checkinDialog.row.lat)
  checkinDialog.lng = (baseLng + (within500m ? 0.00032 : 0.0135)).toFixed(6)
  checkinDialog.lat = (baseLat + (within500m ? 0.00024 : 0.0085)).toFixed(6)
}
function locateBrowserGps() {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位，请用手机浏览器打开后再打卡')
    return
  }
  navigator.geolocation.getCurrentPosition(
    pos => {
      checkinDialog.lng = pos.coords.longitude.toFixed(6)
      checkinDialog.lat = pos.coords.latitude.toFixed(6)
      ElMessage.success('已获取当前定位')
    },
    () => ElMessage.warning('未获得定位权限，请在浏览器设置中允许定位'),
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
  )
}
async function onCheckinPhoto(file) {
  if (!file) return
  checkinDialog.uploading = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const uploaded = await fileApi.upload(fd)
    checkinDialog.photoFileId = uploaded?.id || null
    if (!checkinDialog.photoFileId) ElMessage.error('照片上传失败，请重拍')
  } catch (_) {
    checkinDialog.photoFileId = null
  } finally {
    checkinDialog.uploading = false
  }
}

const checkinPreviewMeters = computed(() => {
  const r = checkinDialog.row
  const lng1 = Number(checkinDialog.lng)
  const lat1 = Number(checkinDialog.lat)
  const lng2 = Number(r?.lng)
  const lat2 = Number(r?.lat)
  if (!checkinDialog.lng || !r?.lng || ![lng1, lat1, lng2, lat2].every(Number.isFinite)) return null
  const toRad = d => (d * Math.PI) / 180
  const radLat1 = toRad(lat1)
  const radLat2 = toRad(lat2)
  const a = radLat1 - radLat2
  const b = toRad(lng1 - lng2)
  const s = 2 * Math.asin(Math.sqrt(Math.sin(a / 2) ** 2 + Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(b / 2) ** 2))
  return Math.round(s * 6378137)
})

const canSubmitCheckin = computed(() => {
  const r = checkinDialog.row
  return !!(r && r.lng && r.lat && checkinDialog.lng && checkinDialog.lat && checkinDialog.photoFileId
    && !checkinDialog.uploading && String(checkinDialog.note || '').trim().length >= 4)
})

async function submitCheckin() {
  if (!canSubmitCheckin.value || checkinDialog.busy) return
  checkinDialog.busy = true
  try {
    const res = await mktPublicSeaApi.visitCheckin(checkinDialog.row.customerId, {
      checkinLng: checkinDialog.lng,
      checkinLat: checkinDialog.lat,
      photoUrl: `file:${checkinDialog.photoFileId}`,
      note: checkinDialog.note
    })
    ElMessage.success(`打卡通过（距门头 ${res.distanceMeters} 米），已获得 ${res.visitProtectDays || 15} 天保护期`)
    checkinDialog.visible = false
    await reloadAll()
  } catch (_) {
    /* request.js 已统一弹出业务错误提示 */
  } finally {
    checkinDialog.busy = false
  }
}

// 5) 单户与批量投喂加盟云仓
const feedDialog = reactive({ visible: false, row: null, batchIds: [], warehouseIds: [], note: '', busy: false })
async function ensureWarehousesLoaded() {
  if (onlineWarehouses.value.length) return
  try {
    const res = await mktWarehouseApi.page({ pageNo: 1, pageSize: 100, joinStatus: 5, projectId: effectiveProjectId.value || undefined })
    onlineWarehouses.value = res?.records || []
  } catch (_) { onlineWarehouses.value = [] }
}
async function openFeedDialog(row) {
  await ensureWarehousesLoaded()
  Object.assign(feedDialog, { visible: true, row, batchIds: [], warehouseIds: [], note: row.blockNote || '', busy: false })
}
async function openBatchFeedDialog() {
  await ensureWarehousesLoaded()
  Object.assign(feedDialog, {
    visible: true,
    row: null,
    batchIds: selectedRows.value.map(r => r.customerId),
    warehouseIds: [],
    note: '广东同产业带集中开放认领',
    busy: false
  })
}
async function submitFeed() {
  if (feedDialog.busy) return
  feedDialog.busy = true
  try {
    if (feedDialog.batchIds.length) {
      await mktPublicSeaApi.batchFeedWarehouses({
        customerIds: feedDialog.batchIds,
        warehouseIds: feedDialog.warehouseIds,
        note: feedDialog.note
      })
      ElMessage.success(`已将 ${feedDialog.batchIds.length} 户货主批量投喂至加盟云仓限时认领池`)
    } else if (feedDialog.row) {
      await mktPublicSeaApi.feedWarehouses(feedDialog.row.customerId, {
        warehouseIds: feedDialog.warehouseIds,
        note: feedDialog.note
      })
      ElMessage.success('已定向投喂至加盟云仓限时认领池')
    }
    feedDialog.visible = false
    await reloadAll()
  } catch (_) {
    /* request.js 已统一弹出业务错误提示 */
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
  } catch (_) {
    /* request.js 已统一弹出业务错误提示 */
  } finally {
    releaseDialog.busy = false
  }
}

watch(() => projectStore.currentProjectId, () => { query.pageNo = 1; reloadAll() })
onMounted(reloadAll)
</script>

<style scoped>
.connector-honest-bar {
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
  border-radius: 8px;
  padding: 8px 14px;
  margin-bottom: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12.5px;
}
.sea-banner {
  background: linear-gradient(90deg, #eff6ff, #f8fafc);
  border: 1px solid #bfdbfe;
  color: #1e3a8a;
  border-radius: 10px;
  padding: 10px 14px;
  margin-bottom: 10px;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}
.sea-banner-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.sea-banner-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.view-mode-seg {
  display: inline-flex;
  background: #dbeafe;
  padding: 3px;
  border-radius: 8px;
  gap: 3px;
}
.seg-btn {
  border: 0;
  background: transparent;
  color: #1e3a8a;
  padding: 5px 11px;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}
.seg-btn.active {
  background: #2563eb;
  color: #fff;
}
.gd-lock-badge {
  background: #dcfce7;
  color: #166534;
  border: 1px solid #bbf7d0;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 600;
}
.gd-zone-bar {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px 12px;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.gd-zone-buttons {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.gd-zone-title {
  font-size: 12.5px;
  font-weight: 700;
  color: #1e3a8a;
}
.zone-chip {
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #334155;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 12px;
  cursor: pointer;
}
.zone-chip.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
  font-weight: 600;
}
.zone-cnt {
  margin-left: 3px;
  opacity: 0.88;
}
.analysis-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.analysis-card {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter, #e2e8f0);
  border-radius: 10px;
  padding: 13px 15px;
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
  white-space: nowrap;
}
.analysis-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 8px 0 4px;
  flex-wrap: wrap;
}
.analysis-value {
  font-size: 22px;
  font-weight: 800;
  color: #0f172a;
}
.analysis-foot, .sub-note {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}
.batch-action-bar {
  margin-top: 8px;
  padding: 8px 12px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12.5px;
  color: #065f46;
}
.batch-btns { display: flex; gap: 8px; flex-wrap: wrap; }
.row-title { font-weight: 600; color: #0f172a; }
.link-title { color: #1d4ed8; cursor: pointer; }
.link-title:hover { text-decoration: underline; }
.tag-inline-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 4px;
}
.shop-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.shop-chip {
  display: inline-block;
  font-size: 11.5px;
  padding: 1px 7px;
  border-radius: 6px;
  background: #eff6ff;
  color: #1d4ed8;
  border: 1px solid #bfdbfe;
}
.shop-chip[data-plat="抖音"] { background: #fff1f2; color: #be123c; border-color: #fecdd3; }
.shop-chip[data-plat="天猫"] { background: #fffbeb; color: #b45309; border-color: #fde68a; }
.shop-chip[data-plat="拼多多"] { background: #f5f3ff; color: #6d28d9; border-color: #ddd6fe; }
.addr-line { font-size: 12.5px; color: #1e293b; }
.geo-pill {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  border-radius: 6px;
  padding: 2px 7px;
  font-size: 11.5px;
  cursor: pointer;
}
.cluster-pill {
  background: #f5f3ff;
  color: #6d28d9;
  border: 1px solid #ddd6fe;
  border-radius: 6px;
  padding: 2px 7px;
  font-size: 11.5px;
  font-weight: 600;
}
.ok-text { color: #16a34a; font-weight: 600; }
.warn-text { color: #d97706; font-weight: 600; }
.urgency-pill {
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: #b45309;
  border-radius: 999px;
  padding: 2px 9px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.urgency-pill[data-level="S"] {
  border-color: #fecdd3;
  background: #fff1f2;
  color: #e11d48;
}
.row-action-col { display: flex; flex-direction: column; gap: 5px; }
.row-action-line { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }

/* 广东高德地推地图视图 */
.map-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 12px;
  min-height: 480px;
}
.map-canvas-wrap {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #cbd5e1;
  min-height: 460px;
  background: #e0f2fe;
}
.map-floating-bar {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  z-index: 5;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.amap-real-canvas, .tactical-map-stage {
  width: 100%;
  height: 100%;
  min-height: 460px;
  position: relative;
}
.tactical-map-stage {
  background:
    radial-gradient(circle at 48% 50%, rgba(37, 99, 235, 0.1) 0%, transparent 55%),
    linear-gradient(0deg, rgba(148, 163, 184, 0.16) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.16) 1px, transparent 1px),
    linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 52%, #ecfdf5 100%);
  background-size: 100% 100%, 36px 36px, 36px 36px, 100% 100%;
}
.wh-golden-circle {
  position: absolute;
  left: 48%;
  top: 50%;
  width: 250px;
  height: 250px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  border: 2px dashed rgba(37, 99, 235, 0.55);
  background: rgba(59, 130, 246, 0.08);
  pointer-events: none;
}
.circle-caption {
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  white-space: nowrap;
  font-size: 11px;
  color: #1d4ed8;
  font-weight: 600;
}
.route-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}
.map-pin {
  position: absolute;
  transform: translate(-50%, -50%);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.18);
  white-space: nowrap;
}
.wh-pin {
  background: #0f172a;
  color: #fff;
  z-index: 3;
}
.cust-pin {
  border: 2px solid #fff;
  background: #2563eb;
  color: #fff;
  cursor: pointer;
  z-index: 4;
}
.cust-pin.cluster { background: #7c3aed; }
.cust-pin.hot { background: #e11d48; }
.cust-pin.active {
  outline: 3px solid #f59e0b;
  transform: translate(-50%, -50%) scale(1.08);
  z-index: 6;
}
.map-honest-foot {
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: 8px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 6px;
  padding: 5px 10px;
  font-size: 11.5px;
  color: #475569;
}
.map-side-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.side-card-head { display: flex; gap: 6px; flex-wrap: wrap; }
.side-cust-name { margin: 4px 0 0; font-size: 15px; color: #0f172a; }
.side-info-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px;
  font-size: 12.5px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.side-btn-stack {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.side-btn-stack :deep(.el-button) { margin-left: 0; width: 100%; }

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
  font-size: 11px;
  font-weight: 700;
  color: #1d4ed8;
  background: #dbeafe;
  padding: 2px 8px;
  border-radius: 999px;
}
.drill-q { margin: 8px 0; font-size: 13px; color: #334155; line-height: 1.6; }
.l2-item {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}
.l2-summary {
  width: 100%;
  border: 0;
  background: #fff;
  padding: 10px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  text-align: left;
  cursor: pointer;
}
.l2-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.expand-hint { font-size: 12px; color: #2563eb; font-weight: 600; }
.l3-panel {
  background: #f8fafc;
  border-top: 1px dashed #cbd5e1;
  padding: 10px 12px;
  font-size: 12px;
  color: #334155;
  display: flex;
  flex-direction: column;
  gap: 5px;
  word-break: break-all;
}
.wash-result-box {
  margin-top: 12px;
  padding: 12px;
  border-radius: 8px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  font-size: 13px;
}
.wash-stats-grid {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.mobile-card-list { display: none; }
@media (max-width: 1100px) {
  .analysis-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .map-workspace { grid-template-columns: 1fr; }
}
@media (max-width: 860px) {
  .analysis-grid { grid-template-columns: 1fr; }
  .desktop-table-wrap { display: none; }
  .mobile-card-list { display: flex; flex-direction: column; gap: 10px; }
  .mobile-sea-card {
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    padding: 12px;
    background: #fff;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .m-card-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
  .m-card-tags, .m-card-actions { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
  .wash-stats-grid { grid-template-columns: 1fr; }
}
</style>
