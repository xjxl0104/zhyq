<template>
  <section v-if="canQuery" class="table-card yunshan-panel" aria-labelledby="yunshan-title">
    <div class="toolbar">
      <h2 id="yunshan-title" class="title">客户发货量同步</h2>
      <el-tag v-if="config" :type="hasConfiguredSource ? 'success' : 'warning'">{{ hasConfiguredSource ? '接口已配置' : '接口未配置' }}</el-tag>
      <el-button :loading="loading.maps" @click="refresh">刷新</el-button>
      <el-button v-if="canConfig" type="primary" @click="openMap">配置客户同步</el-button>
    </div>
    <p class="hint">按客户和作业仓库同步每日发货票数，并核对数据。统计在次日生成；使用上方云仓的发货量接口，无需重复对接。</p>
    <p class="hint">先选择与当前云仓有有效已签合同的客户，自动带出已建档的作业仓库，再核对该客户在接口里的货主和仓库。</p>
    <el-alert v-if="config && !config.configured" type="info" :closable="false" title="该云仓还没有配置发货量接口，请先在页面上方配置。历史快照仍可查询。" />
    <el-alert v-if="errors.maps" type="error" :closable="false" :title="errors.maps" />
    <el-alert v-if="lastRun?.result?.failed > 0" type="error" show-icon :closable="false"
              :title="`上次${lastRun.trigger}生成佣金订单（${lastRun.at}）有 ${lastRun.result.failed} 个发货日失败：${lastRun.result.errors?.[0] || '未知原因'}`"
              description="常见原因：合同还没配置每单总佣金。配置后下一次每天 10:00 的自动生成会补上；失败也会推送告警。" />
    <el-alert v-if="focus && maps.length && !visibleMaps.length" type="info" :closable="false" title="所选客户在该作业仓库尚未配置发货量同步，可点击上方客户列表中的“配置同步”或本区的“配置客户同步”为其配置。" />
    <el-table v-loading="loading.maps" :data="visibleMaps" size="small" border empty-text="暂无云仓发货量同步配置，不影响客户与作业仓库建档。">
      <el-table-column prop="customerName" label="客户（货主）" min-width="130"><template #default="{ row }">{{ row.customerName || row.customerId }}</template></el-table-column>
      <el-table-column label="接口返回的货主" min-width="170"><template #default="{ row }"><div>{{ row.parentOwnerName || '未命名客户' }}</div><div class="entity-no">{{ row.parentOwnerNo }}</div><el-tag v-if="row.identityMismatch" type="danger">客户身份不一致，已阻止同步</el-tag></template></el-table-column>
      <el-table-column label="作业仓库" min-width="170"><template #default="{ row }"><div>{{ row.operationWarehouseName || '历史配置（未关联本地仓库）' }}</div><div class="entity-no">接口仓库：{{ row.parentWarehouseName }} · {{ row.parentWarehouseNo }}</div></template></el-table-column>
      <el-table-column label="接口配置" min-width="140"><template #default="{ row }">{{ row.connectionMode === 'CUSTOM' ? '客户专用接口' : '共享接口' }}<div class="entity-no">{{ row.connectionBaseUrl || config?.baseUrl }}</div></template></el-table-column>
      <el-table-column label="每日拉取" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="最近拉取" min-width="180"><template #default="{ row }">{{ row.lastSyncAt || '尚未拉取' }}<el-tag v-if="row.lastSyncOk != null" class="sync-state" :type="row.lastSyncOk === 1 ? 'success' : 'danger'">{{ row.lastSyncOk === 1 ? '成功' : '失败' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" :width="canConfig ? 150 : 80" fixed="right"><template #default="{ row }">
        <el-button link type="primary" @click="selectMap(row.id)">查看</el-button>
        <el-button v-if="canConfig" link :type="row.status === 1 ? 'warning' : 'success'" :disabled="row.status !== 1 && row.identityMismatch" :loading="statusBusy === String(row.id)" @click="toggleStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
      </template></el-table-column>
    </el-table>

    <template v-if="visibleMaps.length">
      <el-divider />
      <el-form class="query-form" :inline="true" @submit.prevent>
        <el-form-item label="客户关联"><el-select v-model="mapId" aria-label="选择发货量客户关联" class="map-select" @change="changeMap">
          <el-option v-for="item in visibleMaps" :key="item.id" :value="String(item.id)" :label="mapLabel(item)" />
        </el-select></el-form-item>
        <el-form-item label="发货日期"><el-date-picker v-model="dateRange" class="date-range" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" :disabled-date="disabledDate" @change="changeRange" /></el-form-item>
        <el-form-item><el-button :loading="loading.volume" @click="queryVolume">查询快照</el-button><el-button v-if="canConfig" type="primary" :disabled="!sourceReady || selectedMap?.status !== 1 || selectedMap?.identityMismatch" :loading="loading.sync" @click="sync">补拉此区间</el-button><el-button :disabled="!sourceReady || loading.sync || selectedMap?.identityMismatch" :loading="loading.reconcile" @click="reconcile">只读对账</el-button><el-button v-if="canBackfill" :disabled="selectedMap?.status !== 1 || selectedMap?.identityMismatch" @click="openBackfill">历史回补佣金</el-button></el-form-item>
      </el-form>
      <p class="hint range-hint">北京时间，单次最多 92 天。停用关联后保留历史快照；缺少统计的日期不视作零票。</p>
      <el-alert v-if="errors.volume" type="error" :closable="false" :title="errors.volume" />
      <el-alert v-if="syncResult" :type="syncResult.daysMissing ? 'warning' : 'success'" :closable="false" :title="syncMessage" />
      <el-alert v-if="errors.sync" type="error" :closable="false" :title="errors.sync" />
      <div class="volume-summary" aria-live="polite">
        <span>区间已统计票数 <strong>{{ volume ? (volume.dataState === 'NO_STAT_DATA' ? '暂无统计' : `${formatCount(volume.totalConsignNum)} 票`) : '—' }}</strong></span>
        <span v-if="volume">有快照 {{ volume.daysWithData }} 天 · 无快照 {{ volume.daysMissing }} 天</span>
      </div>
      <el-table v-loading="loading.volume" :data="volume?.records || []" size="small" border empty-text="此区间暂无快照。完成补拉后再查询；无统计不代表零发货。">
        <el-table-column prop="statDate" label="发货日期" width="120" />
        <el-table-column label="发货票数" min-width="190" align="right"><template #default="{ row }">{{ formatCount(row.consignNum) }}</template></el-table-column>
        <el-table-column label="已生成佣金（票）" min-width="160" align="right"><template #default="{ row }"><span :class="commissionClass(row)">{{ commissionText(row) }}</span></template></el-table-column>
        <el-table-column label="首次票数" min-width="190" align="right"><template #default="{ row }">{{ formatCount(row.firstConsignNum) }}</template></el-table-column>
        <el-table-column prop="revisedCount" label="修正次数" width="100" align="right" />
        <el-table-column prop="lastRevisedAt" label="最近修正" min-width="170"><template #default="{ row }">{{ row.lastRevisedAt || '—' }}</template></el-table-column>
        <el-table-column prop="fetchedAt" label="最近拉取" min-width="170" />
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="Number(volume?.total || 0)" v-model:current-page="volumePage" :page-size="20" @current-change="loadVolume" />
      <p class="hint">“已生成佣金”对比每天的发货票数与已生成佣金订单的票数。显示“未生成”可能是：当天数据次日才拉取、该合同还没配置每单佣金、不在合同有效期内、该日已有手工导入或 ERP 推送的计佣订单（为避免重复计佣不再按发货量生成），或云山没有返回该日统计（没有快照的日期不会出现在这张表里）。佣金订单每天 10:00 自动生成。</p>

      <div class="monthly-section">
        <div class="toolbar logs-heading"><h3 class="title">月度发货量汇总</h3></div>
        <el-form class="query-form" :inline="true" @submit.prevent>
          <el-form-item label="客户"><el-select v-model="monthly.customerId" clearable placeholder="全部客户" aria-label="选择统计客户" class="map-select" @change="loadMonthly">
            <el-option v-for="item in monthlyCustomers" :key="item.id" :value="item.id" :label="item.name" />
          </el-select></el-form-item>
          <el-form-item label="年份"><el-date-picker v-model="monthly.year" type="year" value-format="YYYY" :clearable="false" aria-label="选择统计年份" @change="loadMonthly" /></el-form-item>
          <el-form-item><el-button :loading="monthly.loading" @click="loadMonthly">刷新汇总</el-button></el-form-item>
        </el-form>
        <el-alert v-if="monthly.error" type="error" :closable="false" :title="monthly.error" />
        <div class="volume-summary" aria-live="polite">
          <span>{{ monthly.year }} 年累计发货 <strong>{{ formatCount(monthly.yearTotal, '0') }} 票</strong></span>
          <span>{{ monthly.customerId ? '当前客户' : '全部客户' }} · 有快照 {{ monthly.days }} 天</span>
        </div>
        <el-table v-loading="monthly.loading" :data="monthly.rows" size="small" border empty-text="暂无月度汇总。">
          <el-table-column prop="month" label="年月" width="120" />
          <el-table-column label="发货票数" min-width="190" align="right"><template #default="{ row }">{{ row.daysWithData ? formatCount(row.tickets) : '无快照' }}</template></el-table-column>
          <el-table-column prop="daysWithData" label="有快照天数" width="120" align="right" />
        </el-table>
        <p class="hint">按发货日期的自然月汇总本地快照；不同客户使用各自 ERP 对接的数据，不选客户则汇总当前云仓全部客户。无快照的月份不代表零发货。</p>
      </div>

      <div v-if="reconciliation || errors.reconcile || loading.reconcile" class="reconcile-section" v-loading="loading.reconcile">
        <h3>只读对账结果</h3>
        <el-alert v-if="errors.reconcile" type="error" :closable="false" :title="errors.reconcile" />
        <template v-if="reconciliation">
          <el-alert v-if="reconciliation.summaryMatchesDaily === false" type="warning" :closable="false" title="接口返回的区间合计与逐日查询快照不一致，可能正在生成或更新统计，请稍后重新对账。" />
          <el-alert :type="reconciliation.dataState === 'NO_STAT_DATA' ? 'warning' : reconciliation.matched ? 'success' : 'warning'" :closable="false" :title="reconciliation.dataState === 'NO_STAT_DATA' ? '接口暂无统计，无法确认一致；已保留本地快照。' : reconciliation.matched ? '本地快照、接口逐日明细及区间合计一致。' : '发现差异，请核对以下日期。对账不会更新快照。'" />
          <dl class="reconcile-totals"><div><dt>本地快照</dt><dd>{{ formatCount(reconciliation.localTotal) }} 票</dd></div><div><dt>接口区间合计</dt><dd>{{ reconciliation.dataState === 'NO_STAT_DATA' ? '暂无统计' : `${formatCount(reconciliation.summaryTotal)} 票` }}</dd></div><div><dt>接口逐日合计</dt><dd>{{ reconciliation.dataState === 'NO_STAT_DATA' ? '暂无统计' : `${formatCount(reconciliation.dailyTotal)} 票` }}</dd></div><div><dt>差额（本地 − 接口）</dt><dd>{{ reconciliation.dataState === 'NO_STAT_DATA' ? '—' : `${formatCount(reconciliation.difference)} 票` }}</dd></div></dl>
          <el-table v-if="reconciliation.differences?.length" :data="reconciliation.differences" size="small" border>
            <el-table-column prop="statDate" label="日期" width="120" />
            <el-table-column label="本地票数" min-width="190" align="right"><template #default="{ row }">{{ formatCount(row.localConsignNum, '无快照') }}</template></el-table-column>
            <el-table-column label="接口票数" min-width="190" align="right"><template #default="{ row }">{{ formatCount(row.remoteConsignNum, '无统计') }}</template></el-table-column>
            <el-table-column label="差异" min-width="160"><template #default="{ row }">{{ differenceLabel(row.reason) }}</template></el-table-column>
          </el-table>
        </template>
      </div>
      <div class="toolbar logs-heading"><h3 class="title">发货量同步日志</h3><el-button size="small" :loading="loading.logs" @click="loadLogs">刷新日志</el-button></div>
      <el-alert v-if="errors.logs" type="error" :closable="false" :title="errors.logs" />
      <el-table v-loading="loading.logs" :data="logs" size="small" border empty-text="此客户关联尚无同步日志。">
        <el-table-column prop="createTime" label="拉取时间" min-width="170" />
        <el-table-column label="方式" width="90"><template #default="{ row }">{{ row.triggerType === 1 ? '定时拉取' : '手动补拉' }}</template></el-table-column>
        <el-table-column label="发货区间" min-width="210"><template #default="{ row }">{{ row.startDate }} 至 {{ row.endDate }}</template></el-table-column>
        <el-table-column label="结果" width="80"><template #default="{ row }"><el-tag :type="row.ok === 1 ? 'success' : 'danger'">{{ row.ok === 1 ? '成功' : '失败' }}</el-tag></template></el-table-column>
        <el-table-column label="票数" min-width="190" align="right"><template #default="{ row }">{{ row.ok === 1 && row.daysFetched === 0 ? '无统计' : formatCount(row.totalConsign) }}</template></el-table-column>
        <el-table-column label="天数（拉取 / 修正 / 缺失）" min-width="190"><template #default="{ row }">{{ row.daysFetched }} / {{ row.daysChanged }} / {{ row.daysMissing }}</template></el-table-column>
        <el-table-column label="说明" min-width="220"><template #default="{ row }">{{ row.errorMsg || row.errorCode || (row.daysFetched === 0 ? '本次未返回统计数据' : '—') }}</template></el-table-column>
      </el-table>
      <p class="hint">日志的“缺失”指本地已有快照、此次接口未返回的日期，原快照会保留。</p>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="logTotal" v-model:current-page="logPage" :page-size="20" @current-change="loadLogs" />
    </template>

    <el-dialog v-model="backfill.visible" title="历史回补佣金" width="min(820px, calc(100vw - 32px))" :close-on-click-modal="false">
      <p class="dialog-hint">每天 10:00 的自动任务只处理最近 35 天。更早且还没生成佣金的日期要先在这里核对每单金额和分成，确认后再回补，避免历史账按错的价格入账。</p>
      <el-alert v-if="backfill.error" type="error" :closable="false" :title="backfill.error" />
      <div v-loading="backfill.loading">
        <el-empty v-if="backfill.preview && !backfill.preview.parts.length" description="没有需要回补的历史日期" />
        <template v-if="backfill.preview?.parts.length">
          <p class="dialog-hint">客户：{{ backfill.preview.customerName }}。将回补 <strong>{{ backfill.preview.rangeFrom }}</strong> 至 <strong>{{ backfill.preview.rangeTo }}</strong> 之间共 {{ backfill.preview.pendingDays }} 个发货日、{{ formatCount(backfill.preview.pendingTickets) }} 票；<span v-if="backfill.preview.skippedDays">另有 {{ backfill.preview.skippedDays }} 天因没有有效合同或已有手工/ERP 计佣订单而跳过。</span></p>
          <div v-for="part in backfill.preview.parts" :key="part.contractId" class="backfill-part">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="合同">{{ part.contractNo }}（{{ part.signMode === 2 ? '云仓直签' : '园区签' }}）</el-descriptions-item>
              <el-descriptions-item label="成交伙伴">{{ part.sellerName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="回补范围">{{ part.firstDay }} 至 {{ part.lastDay }}，{{ part.days }} 天 / {{ formatCount(part.tickets) }} 票</el-descriptions-item>
              <el-descriptions-item label="每单总佣金">{{ part.totalPerOrder == null ? '—' : `${part.totalPerOrder} 元` }}</el-descriptions-item>
              <el-descriptions-item label="平台/公司留存">{{ part.platformPerOrder == null ? '—' : `${part.platformPerOrder} 元/单` }}</el-descriptions-item>
              <el-descriptions-item label="成员合计">{{ part.memberPerOrder == null ? '—' : `${part.memberPerOrder} 元/单` }}</el-descriptions-item>
            </el-descriptions>
            <el-alert v-if="part.blocked" type="error" :closable="false" :title="`无法回补：${part.blocked}。请先到“结算 → 计佣订单 → 佣金分配”配置后再回来。`" />
            <el-table v-else :data="part.members" size="small" border>
              <el-table-column prop="name" label="成员" min-width="110" />
              <el-table-column prop="positionCode" label="岗位" width="70" />
              <el-table-column prop="role" label="角色" width="100" />
              <el-table-column prop="perOrder" label="每单（元）" width="110" align="right" />
              <el-table-column prop="estimated" label="预计回补（元）" width="130" align="right" />
            </el-table>
            <p v-if="!part.blocked" class="hint">预计佣金合计（总佣金）{{ part.estimatedTotal }} 元，其中成员 {{ part.estimatedMembers }} 元，平台/公司留存 {{ (Number(part.estimatedTotal) - Number(part.estimatedMembers)).toFixed(3) }} 元。</p>
          </div>
          <p class="hint">注意：回补使用的是现在的每单金额和分成链路，不是发货当天的；回补后已生成的佣金不会因日后改价自动重算。如需调整，请先到“佣金分配”修改，再回来重新预览。</p>
          <el-checkbox v-model="backfill.confirmed">我已核对以上每单金额、平台留存和各成员分成，同意按此回补</el-checkbox>
        </template>
      </div>
      <template #footer>
        <el-button :disabled="backfill.busy" @click="backfill.visible = false">取消</el-button>
        <el-button type="primary" :loading="backfill.busy" :disabled="!canConfirmBackfill" @click="confirmBackfill">确认回补</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mapDialog.visible" title="配置客户发货量同步" width="min(560px, calc(100vw - 32px))" :close-on-click-modal="false" @closed="closeMap">
      <p class="dialog-hint">加盟云仓：{{ warehouseName || '当前所选云仓' }}。仅为该客户本人配置数据来源，不得使用其他客户的发货数据。</p>
      <el-form label-position="top" @submit.prevent="saveMap">
        <el-form-item label="客户（货主）" required>
          <el-select v-model="mapDialog.customerId" filterable remote :remote-method="searchCustomers" :loading="loading.customers" :disabled="loading.save" placeholder="搜索当前云仓的已签约客户" class="full-width" @change="matchCustomer">
            <el-option v-for="item in customers" :key="item.id" :label="item.name" :value="String(item.id)" />
            <template #footer><el-pagination small layout="prev, pager, next" :total="customerTotal" :page-size="20" v-model:current-page="customerPage" @current-change="loadCustomers" /></template>
          </el-select>
        </el-form-item>
        <p class="hint">仅显示当前云仓有效合同下的客户；没有候选时，请先完成合同签署或直签备案。</p>
        <el-form-item label="已关联作业仓库" required>
          <span v-if="localSites.length === 1">{{ localSites[0].name }}（已自动关联）</span>
          <el-select v-else v-model="mapDialog.operationWarehouseId" :loading="loading.sites" :disabled="!mapDialog.customerId || loading.save" placeholder="选择已建档的作业仓库" class="full-width" @change="mapDialog.identityConfirmed = false">
            <el-option v-for="item in localSites" :key="item.id" :label="item.name" :value="String(item.id)" />
          </el-select>
        </el-form-item>
        <el-alert v-if="mapDialog.customerId && !loading.sites && !localSites.length" type="warning" :closable="false" title="该客户尚无启用的作业仓库，请先在上方“客户与作业仓库”中建档。" />
        <el-divider>配置该客户的发货量接口</el-divider>
        <el-form-item label="连接方式">
          <el-radio-group v-model="connection.mode" :disabled="loading.save">
            <el-radio value="SHARED" :disabled="!config?.configured">使用云仓接口</el-radio><el-radio value="CUSTOM">该客户使用专用接口</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="connection.mode === 'CUSTOM'">
          <el-form-item label="接口基础地址" required><el-input v-model="connection.baseUrl" :disabled="loading.save" maxlength="500" placeholder="接口提供方的基础地址（支持云山协议）" /></el-form-item>
          <el-form-item label="App ID" required><el-input v-model="connection.appId" :disabled="loading.save" maxlength="256" autocomplete="off" /></el-form-item>
          <el-form-item label="RSA 公钥" required><el-input v-model="connection.publicKey" :disabled="loading.save" type="textarea" :rows="3" maxlength="8192" placeholder="粘贴接口提供方的 RSA 公钥" /></el-form-item>
        </template>
        <p v-else-if="config?.configured" class="hint">云仓接口：{{ config.baseUrl }} · {{ config.appId }}</p>
        <el-alert v-else type="warning" :closable="false" title="该云仓还没有配置发货量接口，请先关闭本窗口，在页面上方配置接口。" />
        <el-button :loading="loading.owners" :disabled="!mapDialog.customerId || !connectionReady || loading.save" @click="reloadDirectory">验证接口并读取客户授权</el-button>
        <template v-if="mapDialog.customerId && directoryState.ownersLoaded">
          <el-alert :type="selectedOwner ? 'info' : 'warning'" :closable="false" :title="customerLinkNotice" />
          <el-form-item v-if="matchingOwners.length > 1" label="同名候选（须核实身份）"><el-select v-model="mapDialog.parentOwnerNo" filterable :disabled="loading.save" placeholder="核对接口编号后选择" class="full-width" @change="selectInterfaceCustomer"><el-option v-for="item in matchingOwners" :key="item.no" :label="`${item.name} · ${item.no}`" :value="String(item.no)" /></el-select></el-form-item>
        </template>
        <el-form-item label="接口中的作业仓库" required>
          <span v-if="directoryState.warehousesLoaded && entityWarehouses.length === 1">{{ entityWarehouses[0].name }}（已自动选择）</span>
          <el-select v-else v-model="mapDialog.parentWarehouseNo" filterable :loading="loading.warehouses" :disabled="!mapDialog.parentOwnerNo || loading.warehouses || loading.save" placeholder="选择客户后显示可用仓库" class="full-width" @change="mapDialog.identityConfirmed = false"><el-option v-for="item in entityWarehouses" :key="item.no" :label="`${item.name} · ${item.no}`" :value="String(item.no)" /></el-select>
        </el-form-item>
        <p v-if="directoryState.warehousesLoaded" class="hint" role="status">该客户可用 {{ entityWarehouses.length }} 个作业仓库{{ entityWarehouses.length === 0 ? '，待接口授权：请核对该客户的仓库查询权限。' : entityWarehouses.length === 1 ? '；如需其他仓库，请核对接口授权。' : '，请选择实际承接业务的仓库。' }}</p>
        <el-checkbox v-if="selectedOwner" v-model="mapDialog.identityConfirmed" :disabled="loading.save">我已核实接口货主属于所选客户，接口仓库对应上述已建档作业仓库</el-checkbox>
      </el-form>
      <el-alert v-if="errors.directory" type="error" :closable="false" :title="errors.directory" />
      <el-alert v-if="errors.save" type="error" :closable="false" :title="errors.save" />
      <p class="hint">一个客户可以在多个作业仓库发货。同一组合不重复统计，停用后保留历史。</p>
      <el-checkbox v-model="mapDialog.syncAfterSave" :disabled="loading.save">保存后立即获取近 7 天发货数据（截至昨天）</el-checkbox>
      <div class="directory-tools">
        <span v-if="directoryState.ownersLoaded" class="hint" role="status">当前接口开放 {{ owners.length }} 个客户的查询权限</span>
        <el-button link type="primary" :loading="loading.owners" :disabled="loading.warehouses || loading.save" @click="reloadDirectory">重新读取授权目录</el-button>
      </div>
      <template #footer><el-button :disabled="loading.save" @click="mapDialog.visible = false">取消</el-button><el-button type="primary" :loading="loading.save" :disabled="!canSaveMap" @click="saveMap">{{ mapDialog.syncAfterSave ? '保存并同步' : '保存配置' }}</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { mktYunshanApi } from '@/api/marketing'
import { hasPermission } from '@/utils/permission'

// focus: 上方客户列表选中的行 { customerId, operationWarehouseId, customerName }；为空显示全部客户
const props = defineProps({ warehouseId: { type: [String, Number], required: true }, warehouseName: { type: String, default: '' }, focus: { type: Object, default: null } })
const emit = defineEmits(['changed'])
const canQuery = computed(() => hasPermission('crm:marketing:erp:query'))
const canConfig = computed(() => hasPermission('crm:marketing:erp:config'))
const config = ref(null), maps = ref([]), mapId = ref(''), volume = ref(null), reconciliation = ref(null), syncResult = ref(null)
const logs = ref([]), logTotal = ref(0), logPage = ref(1), volumePage = ref(1), statusBusy = ref('')
const loading = reactive({ maps: false, volume: false, logs: false, sync: false, reconcile: false, owners: false, warehouses: false, customers: false, sites: false, save: false })
const errors = reactive({ maps: '', volume: '', logs: '', sync: '', reconcile: '', directory: '', save: '' })
const mapDialog = reactive({ visible: false, customerId: '', parentOwnerNo: '', parentWarehouseNo: '', identityConfirmed: false, operationWarehouseId: '', syncAfterSave: true })
const connection = reactive({ mode: 'SHARED', baseUrl: '', appId: '', publicKey: '' })
const localSites = ref([])
const connectionReady = computed(() => connection.mode === 'SHARED' ? config.value?.configured : connection.baseUrl.trim() && connection.appId.trim() && connection.publicKey.trim())
const sourceReady = computed(() => selectedMap.value?.connectionMode === 'CUSTOM' || config.value?.configured)
const hasConfiguredSource = computed(() => config.value?.configured || maps.value.some(row => row.connectionMode === 'CUSTOM'))
const owners = ref([]), entityWarehouses = ref([]), customers = ref([]), customerTotal = ref(0), customerPage = ref(1), customerKeyword = ref('')
const directoryState = reactive({ ownersLoaded: false, warehousesLoaded: false })
const selectedCustomer = ref(null), ownerMatchKind = ref('')
const matchingOwners = computed(() => {
  const name = selectedCustomer.value?.name?.trim()
  return name ? owners.value.filter(item => item.name?.trim() === name) : []
})
const selectedOwner = computed(() => matchingOwners.value.find(item => String(item.no) === mapDialog.parentOwnerNo))
const customerLinkNotice = computed(() => {
  if (selectedOwner.value) {
    return `同名候选：${selectedOwner.value.name}（${selectedOwner.value.no}）。同名不代表同一主体，请核实编号和实际业务归属后确认。`
  }
  return ownerMatchKind.value === 'ambiguous'
    ? '找到多个同名候选，请核对该客户的接口编号后选择，不能仅凭名称确定身份。'
    : '当前接口没有该客户的同名记录，不能选择其他客户代替。请先完成客户与作业仓库建档，再由接口提供方核对客户名称、编号和授权。'
})
const versions = { monthly: 0, maps: 0, volume: 0, logs: 0, reconcile: 0, directory: 0, customers: 0, sites: 0 }
let disposed = false
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const shiftDate = (date, days) => new Date(Date.parse(`${date}T00:00:00Z`) + days * 86400000).toISOString().slice(0, 10)
const dateRange = ref([shiftDate(today(), -7), shiftDate(today(), -1)])
const visibleMaps = computed(() => {
  const focus = props.focus
  if (!focus?.customerId) return maps.value
  return maps.value.filter(item => String(item.customerId) === String(focus.customerId)
    && (!focus.operationWarehouseId || item.operationWarehouseId == null || String(item.operationWarehouseId) === String(focus.operationWarehouseId)))
})
const selectedMap = computed(() => maps.value.find(item => String(item.id) === mapId.value))
const canSaveMap = computed(() => canConfig.value && connectionReady.value && !loading.sites && localSites.value.some(item => String(item.id) === mapDialog.operationWarehouseId) && mapDialog.customerId && selectedOwner.value && mapDialog.identityConfirmed && entityWarehouses.value.some(item => String(item.no) === mapDialog.parentWarehouseNo) && !loading.warehouses && !loading.owners)
const syncMessage = computed(() => !syncResult.value ? '' : `补拉完成：${syncResult.value.daysFetched} 天，修正 ${syncResult.value.daysChanged} 天，缺失 ${syncResult.value.daysMissing} 天（保留原快照）。`)
const mapLabel = item => `${item.customerName || item.customerId} · ${item.parentOwnerName || item.parentOwnerNo} / ${item.parentWarehouseName || item.parentWarehouseNo}`
const formatCount = (value, empty = '—') => value == null || value === '' ? empty : String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
const differenceLabel = reason => ({ LOCAL_MISSING: '本地无快照', REMOTE_MISSING: '接口本次无统计', VOLUME_MISMATCH: '票数不同' }[reason] || reason)
const disabledDate = value => {
  const date = `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
  return date > today()
}
function rangeParams() {
  const [startDate, endDate] = dateRange.value || []
  if (!startDate || !endDate || startDate > endDate || endDate > today() || (Date.parse(endDate) - Date.parse(startDate)) / 86400000 >= 92) {
    ElMessage.warning('请选择不晚于今天、跨度不超过 92 天的日期区间'); return null
  }
  return { mapId: mapId.value, startDate, endDate }
}
// 最近一次（定时或手动）生成佣金订单的结果；有失败时在面板顶部提示。读取失败不影响其他功能。
const lastRun = ref(null)
async function loadLastRun() {
  if (!canQuery.value) return
  try { const run = await mktYunshanApi.lastRun(); if (!disposed) lastRun.value = run || null }
  catch { if (!disposed) lastRun.value = null }
}

// 历史回补：先预览（按当前每单金额、平台留存和成员分成估算），勾选确认后才回补
const canBackfill = computed(() => hasPermission('crm:marketing:order:edit'))
const backfill = reactive({ visible: false, loading: false, busy: false, preview: null, error: '', confirmed: false })
const canConfirmBackfill = computed(() => !!backfill.preview?.parts?.length && backfill.confirmed && !backfill.loading && !backfill.busy
  && backfill.preview.parts.every(part => !part.blocked))
async function openBackfill() {
  if (!canBackfill.value || !mapId.value) return
  Object.assign(backfill, { visible: true, loading: true, busy: false, preview: null, error: '', confirmed: false })
  const target = mapId.value
  try {
    const preview = await mktYunshanApi.backfillPreview({ mapId: target })
    if (!disposed && target === mapId.value) backfill.preview = preview
  } catch (error) { if (!disposed) backfill.error = error.message || '回补预览加载失败，请重试。' }
  finally { if (!disposed) backfill.loading = false }
}
async function confirmBackfill() {
  if (!canConfirmBackfill.value) return
  const { rangeFrom, rangeTo } = backfill.preview
  backfill.busy = true; backfill.error = ''
  try {
    const result = await mktYunshanApi.generateOrders({ mapId: mapId.value, startDate: rangeFrom, endDate: rangeTo })
    ElMessage.success(`回补完成：新建 ${result.created} 日，重建 ${result.rebuilt} 日，无变化 ${result.unchanged} 日，跳过 ${result.skipped} 日，失败 ${result.failed} 日`)
    if (result.failed) ElMessage.warning(result.errors?.[0] || '部分日期回补失败，请检查客户佣金配置')
    backfill.visible = false
    await Promise.all([loadVolume(), loadLastRun()])
  } catch (error) { backfill.error = error.message || '回补失败，请重试。' }
  finally { backfill.busy = false }
}

// 按天核对：每个有快照的日期已生成多少票的计佣订单；读取失败只是不显示这一列，不影响快照查询
const dailyOrders = ref({})
async function loadDailyOrders(params) {
  const target = params.mapId
  try {
    const rows = await mktYunshanApi.dailyOrders(params)
    if (!disposed && target === mapId.value) dailyOrders.value = Object.fromEntries((rows || []).map(row => [String(row.statDate), row]))
  } catch { if (!disposed && target === mapId.value) dailyOrders.value = {} }
}
function commissionState(row) {
  const tickets = Number(row.consignNum || 0), done = Number(dailyOrders.value[row.statDate]?.commissionTickets || 0)
  if (tickets === 0) return { text: '—', cls: '' }
  if (done === 0) return { text: '未生成', cls: 'commission-none' }
  if (done === tickets) return { text: `${formatCount(done)} · 已生成`, cls: 'commission-ok' }
  return { text: `${formatCount(done)} · 差 ${formatCount(tickets - done)}`, cls: 'commission-diff' }
}
const commissionText = row => commissionState(row).text
const commissionClass = row => commissionState(row).cls
function resetDetails() {
  for (const key of ['volume', 'logs', 'reconcile']) { versions[key]++; loading[key] = false; errors[key] = '' }
  volume.value = null; reconciliation.value = null; syncResult.value = null; errors.sync = ''; logs.value = []; logTotal.value = 0; dailyOrders.value = {}
}
const monthly = reactive({ customerId: null, year: String(new Date().getFullYear()), rows: [], yearTotal: 0, days: 0, loading: false, error: '' })
const monthlyCustomers = computed(() => [...new Map(maps.value.map(item => [item.customerId, { id: item.customerId, name: item.customerName || String(item.customerId) }])).values()])
async function loadMonthly() {
  if (!canQuery.value || !props.warehouseId) return
  const version = ++versions.monthly; monthly.loading = true; monthly.error = ''
  try {
    const result = await mktYunshanApi.monthly({ warehouseId: props.warehouseId, customerId: monthly.customerId || undefined, year: Number(monthly.year) })
    if (disposed || version !== versions.monthly) return
    monthly.rows = result.months || []; monthly.yearTotal = result.yearTotal ?? 0; monthly.days = result.daysWithData ?? 0
  } catch (error) { if (!disposed && version === versions.monthly) { monthly.rows = []; monthly.yearTotal = 0; monthly.days = 0; monthly.error = error.message || '月度汇总查询失败，请重试。' } }
  finally { if (version === versions.monthly) monthly.loading = false }
}
async function refresh() {
  if (!canQuery.value || !props.warehouseId) return
  const version = ++versions.maps; loading.maps = true; errors.maps = ''
  try {
    const [settings, rows] = await Promise.all([mktYunshanApi.config(String(props.warehouseId)), mktYunshanApi.maps(String(props.warehouseId))])
    if (disposed || version !== versions.maps) return
    config.value = settings; maps.value = rows; loadMonthly(); loadLastRun()
    if (!visibleMaps.value.some(item => String(item.id) === mapId.value)) mapId.value = visibleMaps.value.length ? String(visibleMaps.value[0].id) : ''
    if (mapId.value) await changeMap()
  } catch (error) { if (version === versions.maps && !disposed) errors.maps = error.message || '无法加载发货量接口配置和客户关联，请重试。' }
  finally { if (version === versions.maps) loading.maps = false }
}
function selectMap(id) { mapId.value = String(id); changeMap() }
async function changeMap() { resetDetails(); volumePage.value = 1; logPage.value = 1; if (mapId.value) await Promise.all([loadVolume(), loadLogs()]) }
function changeRange() { versions.reconcile++; reconciliation.value = null; loading.reconcile = false; errors.reconcile = ''; syncResult.value = null; errors.sync = ''; queryVolume() }
function queryVolume() { volumePage.value = 1; volume.value = null; return loadVolume() }
async function loadVolume() {
  const params = rangeParams(); if (!canQuery.value || !mapId.value || !params) return
  const version = ++versions.volume; volume.value = null; loading.volume = true; errors.volume = ''
  try { const result = await mktYunshanApi.volume({ ...params, pageNo: volumePage.value, pageSize: 20 }); if (!disposed && version === versions.volume) { volume.value = result; loadDailyOrders(params) } }
  catch (error) { if (!disposed && version === versions.volume) errors.volume = error.message || '快照查询失败，请重试。' }
  finally { if (version === versions.volume) loading.volume = false }
}
async function loadLogs() {
  if (!canQuery.value || !mapId.value) return
  const version = ++versions.logs; loading.logs = true; errors.logs = ''
  try { const result = await mktYunshanApi.logs({ mapId: mapId.value, pageNo: logPage.value, pageSize: 20 }); if (!disposed && version === versions.logs) { logs.value = result.records; logTotal.value = Number(result.total) } }
  catch (error) { if (!disposed && version === versions.logs) { logs.value = []; errors.logs = error.message || '日志查询失败，请重试。' } }
  finally { if (version === versions.logs) loading.logs = false }
}
async function sync() {
  if (!canConfig.value || !sourceReady.value || loading.sync || selectedMap.value?.status !== 1 || selectedMap.value?.identityMismatch) return
  const params = rangeParams(); if (!params) return
  const warehouse = props.warehouseId, target = mapId.value, range = dateRange.value.join('/'); loading.sync = true; errors.sync = ''; syncResult.value = null
  versions.reconcile++; reconciliation.value = null; loading.reconcile = false; errors.reconcile = ''
  try {
    const result = await mktYunshanApi.sync(target, { startDate: params.startDate, endDate: params.endDate })
    if (disposed || props.warehouseId !== warehouse || target !== mapId.value || dateRange.value.join('/') !== range) return
    syncResult.value = result; reconciliation.value = null; await Promise.all([loadVolume(), loadLogs()]); loadMonthly()
    const rows = await mktYunshanApi.maps(String(warehouse)); if (!disposed && props.warehouseId === warehouse) { maps.value = rows; emit('changed') }
  } catch (error) { if (!disposed && props.warehouseId === warehouse && target === mapId.value && dateRange.value.join('/') === range) { errors.sync = error.message || '补拉失败，请查看拉取日志。'; await loadLogs() } }
  finally { loading.sync = false }
}
async function reconcile() {
  if (!canQuery.value || !sourceReady.value || loading.reconcile || loading.sync || selectedMap.value?.identityMismatch) return
  const params = rangeParams(); if (!params) return
  const version = ++versions.reconcile; loading.reconcile = true; errors.reconcile = ''; reconciliation.value = null
  try { const result = await mktYunshanApi.reconcile(params); if (!disposed && version === versions.reconcile) reconciliation.value = result }
  catch (error) { if (!disposed && version === versions.reconcile) errors.reconcile = error.message || '对账失败，请重试。' }
  finally { if (version === versions.reconcile) loading.reconcile = false }
}
async function toggleStatus(row) {
  if (!canConfig.value || statusBusy.value || (row.status !== 1 && row.identityMismatch)) return
  statusBusy.value = String(row.id); const warehouse = props.warehouseId
  try { await mktYunshanApi.setStatus(String(row.id), row.status === 1 ? 0 : 1); if (warehouse === props.warehouseId && !disposed) { row.status = row.status === 1 ? 0 : 1; emit('changed') } }
  catch (error) { if (!disposed) errors.maps = error.message || '状态更新失败，请重试。' }
  finally { statusBusy.value = '' }
}
// 云山目录固定分页，必须读取所有授权页；所有实体编码原样保留为字符串。
async function allEntities(method, params, version) {
  const rows = []; let current = 1, pages = 1
  do {
    const result = await method({ ...params, current })
    if (disposed || version !== versions.directory || !mapDialog.visible) return null
    rows.push(...result.records.map(item => ({ ...item, no: String(item.no) })))
    pages = Math.max(1, Number(result.pages || 1)); current++
  } while (current <= pages)
  return rows
}
async function openMap() {
  if (!canConfig.value) return
  closeMap()
  Object.assign(mapDialog, { visible: true, customerId: '', operationWarehouseId: '', parentOwnerNo: '', parentWarehouseNo: '', identityConfirmed: false, syncAfterSave: true }); owners.value = []; entityWarehouses.value = []; errors.directory = ''; errors.save = ''
  selectedCustomer.value = null; ownerMatchKind.value = ''; localSites.value = []
  Object.assign(connection, { mode: config.value?.configured ? 'SHARED' : 'CUSTOM', baseUrl: config.value?.baseUrl || '', appId: '', publicKey: '' })
  customerPage.value = 1; customerKeyword.value = ''; customers.value = []; customerTotal.value = 0; await loadCustomers()
  if (props.focus?.customerId) await presetCustomer(props.focus)
}
// 从客户列表带入客户：按客户名搜索后选中，并自动带出作业仓库
async function presetCustomer(focus) {
  if (focus.customerName) { customerKeyword.value = focus.customerName; customerPage.value = 1; await loadCustomers() }
  if (!mapDialog.visible || !customers.value.some(item => String(item.id) === String(focus.customerId))) return
  mapDialog.customerId = String(focus.customerId)
  await matchCustomer()
  if (focus.operationWarehouseId && localSites.value.some(item => String(item.id) === String(focus.operationWarehouseId))) mapDialog.operationWarehouseId = String(focus.operationWarehouseId)
}
async function reloadDirectory() {
  if (!canConfig.value || !connectionReady.value || !mapDialog.customerId || !mapDialog.visible || loading.save) return
  const version = ++versions.directory; loading.owners = true; loading.warehouses = false
  owners.value = []; entityWarehouses.value = []; mapDialog.parentOwnerNo = ''; mapDialog.parentWarehouseNo = ''; mapDialog.identityConfirmed = false; errors.directory = ''
  directoryState.ownersLoaded = false; directoryState.warehousesLoaded = false
  try { const rows = await allEntities(fetchDirectory, {}, version); if (rows) { owners.value = rows; directoryState.ownersLoaded = true } }
  catch (error) { if (version === versions.directory && !disposed) errors.directory = error.message || '授权货主加载失败，请关闭后重试。' }
  finally { if (version === versions.directory) loading.owners = false }
  if (version === versions.directory && directoryState.ownersLoaded && mapDialog.customerId) await matchOwner()
}
function closeMap() { versions.directory++; versions.customers++; versions.sites++; connection.appId = ''; connection.publicKey = ''; localSites.value = []; loading.sites = false; loading.owners = false; loading.warehouses = false; loading.customers = false; directoryState.ownersLoaded = false; directoryState.warehousesLoaded = false }
function fetchDirectory(params) {
  return mktYunshanApi.directory(String(props.warehouseId), { customerId: mapDialog.customerId, connection: { ...connection }, ...params })
}
async function matchCustomer() {
  const version = ++versions.sites, id = mapDialog.customerId
  if (connection.mode === 'CUSTOM' && String(selectedCustomer.value?.id || '') !== id) {
    connection.appId = ''; connection.publicKey = ''
  }
  invalidateConnection()
  localSites.value = []; mapDialog.operationWarehouseId = ''; loading.sites = true
  selectedCustomer.value = customers.value.find(item => String(item.id) === id) || null
  try {
    const rows = await mktYunshanApi.customerSites(String(props.warehouseId), id)
    if (disposed || version !== versions.sites || !mapDialog.visible) return
    localSites.value = rows
    if (rows.length === 1) mapDialog.operationWarehouseId = String(rows[0].id)
  } catch (error) { if (version === versions.sites && !disposed) errors.directory = error.message || '作业仓库加载失败，请重新选择客户。' }
  finally { if (version === versions.sites) loading.sites = false }
  if (version === versions.sites && mapDialog.visible && connectionReady.value) await reloadDirectory()
}
function invalidateConnection() {
  versions.directory++; owners.value = []; entityWarehouses.value = []
  loading.owners = false; loading.warehouses = false
  directoryState.ownersLoaded = false; directoryState.warehousesLoaded = false
  mapDialog.parentOwnerNo = ''; mapDialog.parentWarehouseNo = ''; mapDialog.identityConfirmed = false
  errors.directory = ''; errors.save = ''
}
watch(connection, invalidateConnection, { flush: 'sync' })
async function matchOwner() {
  const id = mapDialog.customerId
  selectedCustomer.value = customers.value.find(item => String(item.id) === id)
    || (String(selectedCustomer.value?.id) === id ? selectedCustomer.value : null)
  mapDialog.parentOwnerNo = ''; mapDialog.identityConfirmed = false; ownerMatchKind.value = ''; errors.save = ''
  // 旧关联可能就是错绑，不能作为身份凭据；严格同名也只给候选，必须单独确认。
  if (matchingOwners.value.length === 1) { mapDialog.parentOwnerNo = String(matchingOwners.value[0].no); ownerMatchKind.value = 'name' }
  else if (matchingOwners.value.length > 1) ownerMatchKind.value = 'ambiguous'
  await changeOwner()
}
async function selectInterfaceCustomer() { ownerMatchKind.value = 'manual'; await changeOwner() }
async function changeOwner() {
  mapDialog.identityConfirmed = false
  mapDialog.parentWarehouseNo = ''; entityWarehouses.value = []; errors.directory = ''; const version = ++versions.directory
  directoryState.warehousesLoaded = false; loading.warehouses = false
  if (!mapDialog.parentOwnerNo) return
  loading.warehouses = true
  try {
    const rows = await allEntities(fetchDirectory, { parentOwnerNo: mapDialog.parentOwnerNo }, version)
    if (rows) {
      entityWarehouses.value = rows; directoryState.warehousesLoaded = true
      if (rows.length === 1) mapDialog.parentWarehouseNo = String(rows[0].no)
    }
  }
  catch (error) { if (version === versions.directory && !disposed) errors.directory = error.message || '授权仓库加载失败，请重新选择货主。' }
  finally { if (version === versions.directory) loading.warehouses = false }
}
function searchCustomers(keyword) { customerKeyword.value = keyword; customerPage.value = 1; return loadCustomers() }
async function loadCustomers() {
  if (!canConfig.value || !mapDialog.visible) return
  const version = ++versions.customers; loading.customers = true
  try { const result = await mktYunshanApi.customers({ warehouseId: String(props.warehouseId), keyword: customerKeyword.value, pageNo: customerPage.value, pageSize: 20 }); if (!disposed && version === versions.customers) { customers.value = result.records; customerTotal.value = Number(result.total) } }
  catch (error) { if (!disposed && version === versions.customers) errors.directory = error.message || '客户加载失败，请重新搜索。' }
  finally { if (version === versions.customers) loading.customers = false }
}
async function saveMap() {
  if (!canSaveMap.value || loading.save) return
  const warehouse = props.warehouseId; loading.save = true; errors.save = ''
  try {
    const createdId = await mktYunshanApi.createMap(String(warehouse), { operationWarehouseId: mapDialog.operationWarehouseId, connection: { ...connection }, customerId: mapDialog.customerId, parentOwnerNo: mapDialog.parentOwnerNo, parentWarehouseNo: mapDialog.parentWarehouseNo, identityConfirmed: true })
    if (!disposed && warehouse === props.warehouseId) {
      const syncNow = mapDialog.syncAfterSave
      ElMessage.success('发货量接口配置已保存'); mapDialog.visible = false; closeMap(); await refresh(); emit('changed')
      if (syncNow && createdId && !disposed && warehouse === props.warehouseId) {
        mapId.value = String(createdId); dateRange.value = [shiftDate(today(), -7), shiftDate(today(), -1)]
        await changeMap(); await sync()
      }
    }
  } catch (error) { if (!disposed && warehouse === props.warehouseId) errors.save = error.message || '客户关联保存失败，请重试。' }
  finally { loading.save = false }
}
watch(() => props.warehouseId, () => { mapId.value = ''; maps.value = []; config.value = null; mapDialog.visible = false; closeMap(); resetDetails(); refresh() }, { immediate: true })
// 上方客户列表选中/取消选中某客户时，本区跟随：只显示该客户的配置、快照与月度汇总
watch(() => props.focus, () => {
  const first = visibleMaps.value[0]
  monthly.customerId = props.focus?.customerId ? (first ? first.customerId : props.focus.customerId) : null
  if (first) selectMap(first.id)
  else { mapId.value = ''; resetDetails() }
  loadMonthly()
})
onBeforeUnmount(() => { disposed = true; Object.keys(versions).forEach(key => versions[key]++) })
defineExpose({ openMap, refresh })
</script>

<style scoped>
.yunshan-panel { min-width: 0; }
.toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.title { font-size: 16px; font-weight: 600; margin: 0 auto 0 0; }
.hint, .config-note, .dialog-hint { color: var(--el-text-color-regular); font-size: 13px; line-height: 1.6; }
.hint { margin: 12px 0; }
.config-note { overflow-wrap: anywhere; }
.entity-no { color: var(--el-text-color-secondary); font-size: 12px; overflow-wrap: anywhere; }
.sync-state { margin-left: 6px; }
.backfill-part { margin: 12px 0; display: grid; gap: 8px; }
.commission-ok { color: var(--el-color-success); }
.commission-none { color: var(--el-color-warning); }
.commission-diff { color: var(--el-color-danger); }
.yunshan-panel :deep(.el-alert) { margin: 12px 0; }
.query-form { display: flex; flex-wrap: wrap; gap: 0 12px; }
.query-form :deep(.el-form-item) { margin-right: 0; }
.map-select { width: 300px; }
.date-range { max-width: 100%; }
.range-hint { margin-top: 0; }
.volume-summary { display: flex; flex-wrap: wrap; gap: 8px 24px; padding: 14px 0; color: var(--el-text-color-regular); font-variant-numeric: tabular-nums; }
.volume-summary strong { margin-left: 8px; color: var(--el-text-color-primary); font-size: 17px; }
.pager { margin-top: 12px; justify-content: flex-end; overflow-x: auto; }
.reconcile-section { margin-top: 24px; }
.reconcile-section h3 { font-size: 15px; margin: 0 0 12px; }
.reconcile-totals { display: flex; flex-wrap: wrap; gap: 16px 32px; margin: 16px 0; font-variant-numeric: tabular-nums; }
.reconcile-totals dt { color: var(--el-text-color-regular); font-size: 13px; }
.reconcile-totals dd { margin: 6px 0 0; font-weight: 600; overflow-wrap: anywhere; }
.logs-heading { margin: 28px 0 12px; }
.full-width { width: 100%; }
.directory-tools { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
@media (max-width: 640px) {
  .toolbar .title { flex-basis: 100%; }
  .query-form :deep(.el-form-item) { width: 100%; }
  .query-form :deep(.el-form-item__content) { min-width: 0; flex-wrap: wrap; gap: 8px; }
  .query-form :deep(.el-button + .el-button) { margin-left: 0; }
  .map-select { width: 100%; }
  .date-range { width: 100%; }
  .volume-summary strong { display: inline-block; overflow-wrap: anywhere; }
}
</style>
