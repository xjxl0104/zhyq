// 营销看板 4 张核心经营分析结论卡 —— 三层下钻（L1 结论 → L2 判据论证 → L3 四项物证）纯函数装配模块。
// 严格对齐 MktDashboardController.summary 返回的 drilldown 同源字段：
// - 净毛利：serviceFeeTotal / warehouseSettlementCost / validCommissionTotal / netRetainedMargin / marginRatePct
// - 双轨对账：yunshanActiveMaps / yunshanTotalTickets / erpLiveWarehouses / erpDiffSnapshots / erpUnmappedPending
// - 非出库佣金池：nonOutboundSettleableAmount / nonOutboundSettleableCount
// - 3个月锁客转化：activeLocksCount / dealLocksCount / releasedLocksCount
// 卡面（L1）与侧滑抽屉（L2/L3）共用本函数同源产出，后端字段缺失时优雅兜底为 0，抽屉内零二次重算。

function pickFirst(sources, keys, fallback = 0) {
  for (const src of sources) {
    if (!src || typeof src !== 'object') continue
    for (const k of keys) {
      if (src[k] !== undefined && src[k] !== null && src[k] !== '') {
        return src[k]
      }
    }
  }
  return fallback
}

function toNum(val, fallback = 0) {
  const n = Number(val)
  return Number.isFinite(n) ? n : fallback
}

export function fmtMoney(val) {
  return toNum(val, 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function fmtRate(val) {
  return `${toNum(val, 0).toFixed(1)}%`
}

export function fmtInt(val) {
  return Math.trunc(toNum(val, 0)).toLocaleString('zh-CN')
}

export function buildDashboardDrilldownCards(summaryInput = {}, rangeLabel = '近 30 天') {
  const summary = summaryInput && typeof summaryInput === 'object' ? summaryInput : {}
  const d = summary.drilldown && typeof summary.drilldown === 'object' ? summary.drilldown : {}

  const marginSrc = [d.margin, d, summary]
  const recSrc = [d.reconciliation, d.reconcile, d]
  const nonOutSrc = [d.nonOutbound, d.nonOutboundPool, d]
  const lockSrc = [d.lockConversion, d.customerLock, d]

  const fetchedNote = pickFirst(
    [d, summary],
    ['fetchedAt', 'generatedAt', 'asOf'],
    '实时 SQL 同源聚合（/crm/marketing/dashboard/summary 当次响应）'
  )

  // ---------------- 1. 园区净留存毛利与健康度 ----------------
  const serviceFeeTotal = toNum(pickFirst(marginSrc, ['serviceFeeTotal', 'serviceFee'], 0))
  const warehouseSettlementCost = toNum(pickFirst(marginSrc, ['warehouseSettlementCost', 'warehousePayable', 'warehouseCost'], 0))
  const validCommissionTotal = toNum(pickFirst(marginSrc, ['validCommissionTotal', 'commissionExpense', 'commissionPaid'], 0))
  const netRetainedMargin = toNum(pickFirst(marginSrc, ['netRetainedMargin', 'netMargin', 'grossProfit'], 0))
  const marginRatePct = toNum(pickFirst(marginSrc, ['marginRatePct', 'retainRate', 'marginRate'], 0))
  const healthLabel = netRetainedMargin < 0
    ? '毛利倒挂预警'
    : (marginRatePct >= 15 ? '留存健康' : (serviceFeeTotal === 0 ? '暂无营收样本' : '低毛利关注'))
  const healthTone = netRetainedMargin < 0 ? 'danger' : (marginRatePct >= 15 ? 'success' : 'warning')

  const cardMargin = {
    key: 'margin',
    title: '园区净留存毛利与健康度',
    primaryText: `¥${fmtMoney(netRetainedMargin)}`,
    badgeText: `留存率 ${fmtRate(marginRatePct)} · ${healthLabel}`,
    badgeType: healthTone,
    footText: `服务费 ¥${fmtMoney(serviceFeeTotal)} · 云仓成本 ¥${fmtMoney(warehouseSettlementCost)} · 有效佣金 ¥${fmtMoney(validCommissionTotal)}`,
    question: `${rangeLabel}内，园区全民营销业务扣除应付云仓结算成本与有效伙伴佣金后的净留存毛利是多少？是否健康？`,
    logicNote: '原样搬运后端 summary.drilldown 同源聚合结果：净留存毛利 = 已确认出库服务费收入 − 云仓结算单成本 − 未作废有效伙伴佣金；留存率 = 净留存毛利 ÷ 服务费收入。',
    rows: [
      {
        key: 'margin-net',
        label: '园区净留存毛利 (netRetainedMargin)',
        value: `¥${fmtMoney(netRetainedMargin)}`,
        verdict: healthLabel,
        evidence: {
          raw: JSON.stringify({
            serviceFeeTotal,
            warehouseSettlementCost,
            validCommissionTotal,
            netRetainedMargin,
            marginRatePct
          }),
          source: `数据表：crm_referral_order + crm_warehouse_settlement + crm_promoter_commission · ${fetchedNote}`,
          formula: `净留存毛利 = 服务费 ¥${fmtMoney(serviceFeeTotal)} − 云仓结算成本 ¥${fmtMoney(warehouseSettlementCost)} − 有效伙伴佣金 ¥${fmtMoney(validCommissionTotal)} = ¥${fmtMoney(netRetainedMargin)}`,
          linkLabel: '前往服务费账单台账核查',
          to: { path: '/crm/marketing/bill' }
        }
      },
      {
        key: 'margin-rate',
        label: '毛利留存率 (marginRatePct)',
        value: fmtRate(marginRatePct),
        verdict: marginRatePct >= 15 ? '留存水位健康（≥15%）' : '关注云仓结算与分佣占比',
        evidence: {
          raw: JSON.stringify({ marginRatePct, netRetainedMargin, serviceFeeTotal }),
          source: `接口：GET /crm/marketing/dashboard/summary (drilldown.marginRatePct) · ${fetchedNote}`,
          formula: `毛利留存率 = 净留存毛利 ¥${fmtMoney(netRetainedMargin)} ÷ 服务费收入 ¥${fmtMoney(serviceFeeTotal)} × 100% = ${fmtRate(marginRatePct)}`,
          linkLabel: '前往云仓结算单台账核查',
          to: { path: '/crm/marketing/settlement' }
        }
      },
      {
        key: 'margin-comm',
        label: '有效伙伴佣金总额 (validCommissionTotal)',
        value: `¥${fmtMoney(validCommissionTotal)}`,
        verdict: '排除已作废佣金（含负向扣回冲抵）',
        evidence: {
          raw: JSON.stringify({ validCommissionTotal }),
          source: `数据表：crm_promoter_commission (deleted = 0 AND status <> 5) · ${fetchedNote}`,
          formula: null,
          linkLabel: '前往伙伴佣金结算台账',
          to: { path: '/crm/marketing/commission' }
        }
      }
    ]
  }

  // ---------------- 2. 双轨单量对账一致率 ----------------
  const yunshanActiveMaps = toNum(pickFirst(recSrc, ['yunshanActiveMaps', 'activeYunshanMaps'], 0))
  const yunshanTotalTickets = toNum(pickFirst(recSrc, ['yunshanTotalTickets', 'yunshanTickets'], 0))
  const erpLiveWarehouses = toNum(pickFirst(recSrc, ['erpLiveWarehouses', 'liveErpWarehouses'], 0))
  const erpDiffSnapshots = toNum(pickFirst(recSrc, ['erpDiffSnapshots', 'diffSnapshots'], 0))
  const erpUnmappedPending = toNum(pickFirst(recSrc, ['erpUnmappedPending', 'unmappedPending'], 0))

  const explicitRecRate = pickFirst(recSrc, ['consistencyRate', 'reconcileRate', 'matchRate'], null)
  const totalCheckUnits = yunshanActiveMaps + erpLiveWarehouses + erpDiffSnapshots + erpUnmappedPending
  const issueUnits = erpDiffSnapshots + erpUnmappedPending
  const consistencyRate = explicitRecRate !== null
    ? toNum(explicitRecRate, 0)
    : (totalCheckUnits === 0 ? 0 : Math.max(0, (totalCheckUnits - issueUnits) / totalCheckUnits * 100))
  const recHealthy = issueUnits === 0

  const cardReconcile = {
    key: 'reconcile',
    title: '双轨单量对账一致率',
    primaryText: fmtRate(consistencyRate),
    badgeText: recHealthy ? '零差异阻塞' : `差异快照 ${fmtInt(erpDiffSnapshots)} · 未映射 ${fmtInt(erpUnmappedPending)}`,
    badgeType: recHealthy ? 'success' : 'warning',
    footText: `云山启用映射 ${fmtInt(yunshanActiveMaps)} 项 (${fmtInt(yunshanTotalTickets)} 票) · 正式 ERP 仓 ${fmtInt(erpLiveWarehouses)} 家`,
    question: '「云山发货量同步」与「通用 ERP Webhook 接入」双轨单量是否对齐？是否存在超阈值差异快照或未映射积压？',
    logicNote: '合并展示云山主动拉取启用映射（crm_yunshan_volume_map）与通用 ERP 正式联通仓、周期对账超差快照（crm_erp_reconcile_snapshot 差异 ≥ 2% 未确认）及未映射待处理事件（crm_erp_unmapped OPEN）。',
    rows: [
      {
        key: 'rec-yunshan',
        label: '☁️ 云山发货量启用映射与周期票数',
        value: `${fmtInt(yunshanActiveMaps)} 项 · ${fmtInt(yunshanTotalTickets)} 票`,
        verdict: yunshanActiveMaps > 0 ? '每日拉取云山日汇总正常' : '当前无启用中的云山映射',
        evidence: {
          raw: JSON.stringify({ yunshanActiveMaps, yunshanTotalTickets }),
          source: `数据表：crm_yunshan_volume_map (status=1) + crm_yunshan_daily_volume · ${fetchedNote}`,
          formula: null,
          linkLabel: '前往云山发货量同步轨道',
          to: { path: '/crm/marketing/erp', query: { source: 'yunshan' } }
        }
      },
      {
        key: 'rec-erp-diff',
        label: '🔗 通用 ERP 差异快照与未映射积压',
        value: `差异快照 ${fmtInt(erpDiffSnapshots)} 份 · 未映射 ${fmtInt(erpUnmappedPending)} 条`,
        verdict: recHealthy ? '全部强对账通过' : '存在未确认差异或未映射货主编码，需处置',
        evidence: {
          raw: JSON.stringify({ erpLiveWarehouses, erpDiffSnapshots, erpUnmappedPending }),
          source: `数据表：crm_warehouse + crm_erp_reconcile_snapshot + crm_erp_unmapped · ${fetchedNote}`,
          formula: `对账一致率 = (活跃核验单元 ${fmtInt(totalCheckUnits)} − 异常单元 ${fmtInt(issueUnits)}) ÷ ${fmtInt(totalCheckUnits || 1)} × 100% = ${fmtRate(consistencyRate)}`,
          linkLabel: '前往通用 ERP Webhook 对账轨道',
          to: { path: '/crm/marketing/erp', query: { source: 'webhook' } }
        }
      }
    ]
  }

  // ---------------- 3. 非出库待结算佣金池 ----------------
  const nonOutboundAmount = toNum(pickFirst(nonOutSrc, ['nonOutboundSettleableAmount', 'pendingAmount', 'nonOutboundPendingAmount'], 0))
  const nonOutboundCount = toNum(pickFirst(nonOutSrc, ['nonOutboundSettleableCount', 'pendingCount', 'nonOutboundPendingCount'], 0))

  const cardNonOutbound = {
    key: 'nonOutbound',
    title: '非出库待结算佣金池',
    primaryText: `¥${fmtMoney(nonOutboundAmount)}`,
    badgeText: `${fmtInt(nonOutboundCount)} 笔待结`,
    badgeType: nonOutboundCount > 0 ? 'primary' : 'info',
    footText: `涵盖园区租赁 / 云仓直签实收平台费 / 入仓签约奖可结算流水`,
    question: '园区租赁、云仓直签平台费与入仓签约奖三类非出库佣金中，有多少已解冻待结算入池的金额与笔数？',
    logicNote: '直接聚合 crm_promoter_commission JOIN crm_referral_order 中 source_type IN (1:园区租赁, 3:直签平台费, 4:入仓签约奖) 且 status = 2（已解冻可结算）、sign = 1、未关联提现单的正向待结流水。',
    rows: [
      {
        key: 'nonout-settleable',
        label: '非出库已解冻待结算金额与笔数',
        value: `¥${fmtMoney(nonOutboundAmount)}（${fmtInt(nonOutboundCount)} 笔）`,
        verdict: nonOutboundCount > 0 ? '可前往非出库结算视图一键入池' : '当前无积压待结非出库佣金',
        evidence: {
          raw: JSON.stringify({
            nonOutboundSettleableAmount: nonOutboundAmount,
            nonOutboundSettleableCount: nonOutboundCount
          }),
          source: `数据表：crm_promoter_commission c JOIN crm_referral_order o (source_type IN 1,3,4 AND c.status = 2) · ${fetchedNote}`,
          formula: `非出库待结算池 = SUM(c.amount) WHERE source_type IN (1,3,4) AND status = 2 = ¥${fmtMoney(nonOutboundAmount)}（共 ${fmtInt(nonOutboundCount)} 笔）`,
          linkLabel: '前往伙伴佣金结算（非出库专页）',
          to: { path: '/crm/marketing/commission', query: { view: 'nonOutbound' } }
        }
      },
      {
        key: 'nonout-contracts',
        label: '关联服务合同与直签平台费收款',
        value: '按实收解冻计佣',
        verdict: '直签合同仅按实收平台费计佣，不重复计出库佣金',
        evidence: {
          raw: JSON.stringify({ sourceTypes: [1, 3, 4], labels: ['园区租赁', '云仓直签平台费', '入仓签约奖'] }),
          source: `数据表：crm_service_contract + crm_direct_sign_payment + biz_contract · ${fetchedNote}`,
          formula: null,
          linkLabel: '前往服务合同台账核对',
          to: { path: '/crm/marketing/contract' }
        }
      }
    ]
  }

  // ---------------- 4. 3 个月锁客转化率 ----------------
  const activeLocksCount = toNum(pickFirst(lockSrc, ['activeLocksCount', 'lockingCount', 'inLockCount'], 0))
  const dealLocksCount = toNum(pickFirst(lockSrc, ['dealLocksCount', 'convertedCount', 'longTermLocks'], 0))
  const releasedLocksCount = toNum(pickFirst(lockSrc, ['releasedLocksCount', 'releasedCount', 'publicSeaCount'], 0))
  const publicSeaActiveTotal = toNum(pickFirst([d, summary], ['publicSeaActiveTotal'], 0))
  const publicSeaClaimedCount = toNum(pickFirst([d, summary], ['publicSeaClaimedCount'], 0))
  const publicSeaSignedCount = toNum(pickFirst([d, summary], ['publicSeaSignedCount'], 0))
  const totalLocks = activeLocksCount + dealLocksCount + releasedLocksCount
  const explicitLockRate = pickFirst(lockSrc, ['conversionRate', 'lockConversionRate'], null)
  const lockConversionRate = explicitLockRate !== null
    ? toNum(explicitLockRate, 0)
    : (totalLocks > 0 ? (dealLocksCount / totalLocks * 100) : 0)

  const cardLock = {
    key: 'lockConversion',
    title: '3 个月锁客转化率',
    primaryText: fmtRate(lockConversionRate),
    badgeText: `已成交长期锁 ${fmtInt(dealLocksCount)} 户`,
    badgeType: dealLocksCount > 0 ? 'success' : 'info',
    footText: `在锁 ${fmtInt(activeLocksCount)} 户 vs 已成交长期锁 ${fmtInt(dealLocksCount)} 户 vs 公海释放 ${fmtInt(releasedLocksCount)} 户`,
    question: '伙伴报备客户在 3 个月保护期内有多少成功签约转为长期锁定，多少到期未签约释放回公海？',
    logicNote: '直接读取 crm_customer_lock 各状态户数：status=2 为 3 个月保护期在锁户数，status=3 为已签约成交长期有效锁户数，status=4 为到期或人工释放回公海户数。',
    rows: [
      {
        key: 'lock-rate',
        label: '3 个月锁客成交转化率',
        value: fmtRate(lockConversionRate),
        verdict: `总锁客样本 ${fmtInt(totalLocks)} 户`,
        evidence: {
          raw: JSON.stringify({
            activeLocksCount,
            dealLocksCount,
            releasedLocksCount
          }),
          source: `数据表：crm_customer_lock (deleted = 0, status IN 2,3,4) · ${fetchedNote}`,
          formula: `锁客转化率 = 已成交长期锁 ${fmtInt(dealLocksCount)} 户 ÷ (在锁 ${fmtInt(activeLocksCount)} + 已成交 ${fmtInt(dealLocksCount)} + 公海释放 ${fmtInt(releasedLocksCount)}) × 100% = ${fmtRate(lockConversionRate)}`,
          linkLabel: '前往客户管理查看锁定状态',
          to: { path: '/crm/marketing/customer' }
        }
      },
      {
        key: 'lock-breakdown',
        label: '在锁跟进 vs 公海释放可重报对比',
        value: `在锁 ${fmtInt(activeLocksCount)} 户 / 公海释放 ${fmtInt(releasedLocksCount)} 户`,
        verdict: releasedLocksCount > 0 ? '公海释放客户支持其他伙伴重新报备锁定' : '暂无到期释放客户',
        evidence: {
          raw: JSON.stringify({ activeLocksCount, releasedLocksCount }),
          source: `数据表：crm_customer_lock (status = 2 vs status = 4) · ${fetchedNote}`,
          formula: null,
          linkLabel: '前往客户管理查看「公海可重报」客户',
          to: { path: '/crm/marketing/customer' }
        }
      },
      {
        key: 'lock-public-sea',
        label: '🌊 内部专属公海（免 70% 外部团队佣金）协同沉淀',
        value: `活跃池 ${fmtInt(publicSeaActiveTotal)} 户 · 在跟 ${fmtInt(publicSeaClaimedCount)} 户 · 已签约 ${fmtInt(publicSeaSignedCount)} 户`,
        verdict: publicSeaSignedCount > 0 ? '内部直营公海已跑通 0 外部佣金成单闭环' : '公海池持续供给直营招商与严选云仓',
        evidence: {
          raw: JSON.stringify({ publicSeaActiveTotal, publicSeaClaimedCount, publicSeaSignedCount }),
          source: `数据表：crm_public_sea_meta JOIN crm_customer (deleted=0, referrer_id IS NULL) · ${fetchedNote}`,
          formula: `直营公海签约占比 = 已签约 ${fmtInt(publicSeaSignedCount)} ÷ 活跃公海池 ${fmtInt(publicSeaActiveTotal || 1)} × 100% = ${fmtRate(publicSeaActiveTotal > 0 ? (publicSeaSignedCount / publicSeaActiveTotal * 100) : 0)}`,
          linkLabel: '前往公海线索池作战台',
          to: { path: '/crm/marketing/public-sea' }
        }
      }
    ]
  }

  return [cardMargin, cardReconcile, cardNonOutbound, cardLock]
}
