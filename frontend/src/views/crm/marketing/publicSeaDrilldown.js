// 内部专属公海池 3 张核心分析结论卡 —— 三层下钻（L1 结论 → L2 判据论证 → L3 五联物证）纯函数同源装配模块。
// 严格对齐 MktPublicSeaController.summary 返回的同源字段，抽屉内只搬运不二次重算。

function toNum(v, def = 0) {
  const n = Number(v)
  return Number.isFinite(n) ? n : def
}

export function fmtInt(v) {
  return Math.trunc(toNum(v, 0)).toLocaleString('zh-CN')
}

export function fmtPct(num, den) {
  const d = toNum(den, 0)
  if (d <= 0) return '0.0%'
  return `${((toNum(num, 0) / d) * 100).toFixed(1)}%`
}

export function buildPublicSeaDrilldownCards(rawSummary = {}) {
  const s = rawSummary && typeof rawSummary === 'object' ? rawSummary : {}
  const fetchedAt = s.fetchedAt || '实时 SQL 同源聚合（/crm/marketing/public-sea/summary 当次响应）'

  // -------- 1. 公海有效活水池构成与供给健康度 --------
  const activeTotal = toNum(s.activeTotal)
  const calcHot = toNum(s.calcHotCount)
  const chanmama = toNum(s.chanmamaCount)
  const tungeeJob = toNum(s.tungeeJobCount)
  const expireReturn = toNum(s.expireReturnCount)
  const parkCrm = toNum(s.parkCrmCount)
  const hotRateStr = fmtPct(calcHot, activeTotal)
  const poolBadge = activeTotal === 0
    ? { text: '待补充数据源', type: 'info' }
    : calcHot > 0
      ? { text: `🔥 热询盘 ${hotRateStr}`, type: 'danger' }
      : { text: '供给正常', type: 'success' }

  const poolCard = {
    key: 'pool',
    title: '公海有效活水池（5源归集）',
    primaryText: `${fmtInt(activeTotal)} 户`,
    badgeText: poolBadge.text,
    badgeType: poolBadge.type,
    footText: `算价热留资 ${fmtInt(calcHot)} · 蝉妈妈痛点店 ${fmtInt(chanmama)} · 招打包工 ${fmtInt(tungeeJob)} · 回流调剂 ${fmtInt(expireReturn + parkCrm)}`,
    question: '当前内部公海池的可跟进货主从哪 5 条数据源管线归集而来？热询盘占比如何计算？',
    logicNote: '直接读取 crm_public_sea_meta 关联 crm_customer（referrer_id IS NULL 且 status=1 未归档冷宫）按 sea_source 同源计数；对外部 P1–P4 完全不可见。',
    rows: [
      {
        key: 'pool-hot',
        label: '🔥 30秒云仓算价器主动留资',
        value: `${fmtInt(calcHot)} 户 (${hotRateStr})`,
        verdict: calcHot > 0 ? '最高转化热源：货主刚测完底价并授权手机号申领试发券' : '暂无线上算价留资，可投放 30 秒算价页截流',
        evidence: {
          rawTable: `sea_source='CALC_HOT', count=${calcHot}, activeTotal=${activeTotal}`,
          sourceNote: `公开算价入口 /open/public-calc/lead · 统计时间 ${fetchedAt}`,
          formula: activeTotal > 0 ? `热询盘占比 = ${calcHot} ÷ ${activeTotal} × 100% = ${hotRateStr}` : '分母 activeTotal = 0，占比记 0.0%',
          actionLabel: '筛选查看「30秒算价器留资」客户',
          filterSource: 'CALC_HOT',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'pool-chanmama',
        label: '🎯 蝉妈妈低物流分 + 探迹实名清洗',
        value: `${fmtInt(chanmama)} 户`,
        verdict: '精准痛点源：月销 3,000~150,000 单且店铺物流体验分偏低的电商主体',
        evidence: {
          rawTable: `sea_source='CHANMAMA', count=${chanmama}`,
          sourceNote: `Excel/JSON 防撞清洗入池 · 统计时间 ${fetchedAt}`,
          formula: activeTotal > 0 ? `渠道占比 = ${chanmama} ÷ ${activeTotal} × 100% = ${fmtPct(chanmama, activeTotal)}` : null,
          actionLabel: '筛选查看「蝉妈妈+探迹」客户',
          filterSource: 'CHANMAMA',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'pool-tungee',
        label: '📦 探迹“正在招打包工”特征库',
        value: `${fmtInt(tungeeJob)} 户`,
        verdict: '高替代意愿源：近期发布打包工/仓管招聘，自包人力成本高于园区云仓',
        evidence: {
          rawTable: `sea_source='TUNGEE_JOB', count=${tungeeJob}`,
          sourceNote: `企服招聘特征清洗导入 · 统计时间 ${fetchedAt}`,
          formula: null,
          actionLabel: '筛选查看「招打包工特征库」客户',
          filterSource: 'TUNGEE_JOB',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'pool-return',
        label: '♻️ 外部伙伴超期/弃单回流 + 园区调剂',
        value: `${fmtInt(expireReturn + parkCrm)} 户`,
        verdict: `零成本盘活内部存量（伙伴超期回流 ${fmtInt(expireReturn)} 户 + 园区看房/拒单调剂 ${fmtInt(parkCrm)} 户）`,
        evidence: {
          rawTable: `EXPIRE_RETURN=${expireReturn}, PARK_CRM_OR_MANUAL=${parkCrm}`,
          sourceNote: `LockExpireJob 自动回流与运营录入 · 统计时间 ${fetchedAt}`,
          formula: `内部回流合计 = ${expireReturn} + ${parkCrm} = ${expireReturn + parkCrm} 户（成单 0 外部团队佣金）`,
          actionLabel: '筛选查看「伙伴超期回流」客户',
          filterSource: 'EXPIRE_RETURN',
          aiNote: '规则直算 · 零模型调用'
        }
      }
    ]
  }

  // -------- 2. 入池三道防撞净拦截率 --------
  const washTotal = toNum(s.washTotalRows)
  const washInserted = toNum(s.washInsertedRows)
  const blkPrivate = toNum(s.blockedPrivateLock)
  const blkHmac = toNum(s.blockedWhHmac)
  const blkAgency = toNum(s.blockedDupAgency)
  const totalBlocked = blkPrivate + blkHmac + blkAgency
  const blockRateStr = fmtPct(totalBlocked, washTotal)

  const washCard = {
    key: 'wash',
    title: '入池三道闸防撞净拦截率',
    primaryText: blockRateStr,
    badgeText: '0 抢单冲突',
    badgeType: 'success',
    footText: `近30天洗数 ${fmtInt(washTotal)} 行 → 护私海 ${fmtInt(blkPrivate)} · 护云仓老客 ${fmtInt(blkHmac)} · 剔代账号 ${fmtInt(blkAgency)}`,
    question: '外部名单导入与公开算价留资时，三道防撞闸门分别拦截了多少条数据以保护私海伙伴和加盟云仓老客？',
    logicNote: '汇总近 30 天 crm_public_sea_wash_batch 审计记录：每行先验代账重号，再盲比对云仓 AES+HMAC 白名单，最后核验 90 天在锁私海与已签约库。',
    rows: [
      {
        key: 'wash-private',
        label: '闸门①：保护外部 P1–P4 在锁私海与已签客户',
        value: `拦截 ${fmtInt(blkPrivate)} 行`,
        verdict: '命中 90 天保护期在锁或生效合同直接跳过，绝不冲掉外部伙伴客户归属',
        evidence: {
          rawTable: `blocked_private_lock=${blkPrivate}, wash_total_rows=${washTotal}`,
          sourceNote: `crm_public_sea_wash_batch 近 30 天累计 · ${fetchedAt}`,
          formula: washTotal > 0 ? `私海护客占比 = ${blkPrivate} ÷ ${washTotal} × 100% = ${fmtPct(blkPrivate, washTotal)}` : null,
          actionLabel: '前往「客户管理」核对在锁归属',
          toPath: '/crm/marketing/customer',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'wash-hmac',
        label: '闸门②：保护加盟云仓 AES+HMAC 保密老客白名单',
        value: `拦截 ${fmtInt(blkHmac)} 行`,
        verdict: '通过企业名/品牌名 HMAC 指纹毫秒级盲筛，不暴露云仓老客明文',
        evidence: {
          rawTable: `blocked_wh_hmac=${blkHmac}, wash_total_rows=${washTotal}`,
          sourceNote: `MktCompetitionProtectionService 指纹核验 · ${fetchedAt}`,
          formula: washTotal > 0 ? `云仓老客拦截占比 = ${blkHmac} ÷ ${washTotal} × 100% = ${fmtPct(blkHmac, washTotal)}` : null,
          actionLabel: null,
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'wash-agency',
        label: '闸门③：剔除代理记账高频重号与冷宫死单',
        value: `过滤 ${fmtInt(blkAgency)} 行`,
        verdict: `净入公海 ${fmtInt(washInserted)} 户（同一号码关联 >3 家企业自动判为代账财务号剔除）`,
        evidence: {
          rawTable: `blocked_dup_agency=${blkAgency}, inserted_rows=${washInserted}, total_rows=${washTotal}`,
          sourceNote: `MktPublicSeaWashService 洗数规则 · ${fetchedAt}`,
          formula: washTotal > 0 ? `综合拦截率 = (${blkPrivate} + ${blkHmac} + ${blkAgency}) ÷ ${washTotal} × 100% = ${blockRateStr}` : '近30天暂无导入批次，记 0.0%',
          actionLabel: '打开「Excel 防撞清洗导入」执行新批次',
          openImport: true,
          aiNote: '规则直算 · 零模型调用'
        }
      }
    ]
  }

  // -------- 3. 认领 72h 首触达履约率 --------
  const slaHours = toNum(s.slaHours, 72)
  const whDailyCap = toNum(s.whDailyCap, 5)
  const whActiveCap = toNum(s.whActiveCap, 10)
  const adminClaimed = toNum(s.adminClaimedTotal)
  const adminTouched = toNum(s.adminTouchedCount)
  const whClaimed = toNum(s.whClaimedTotal)
  const whTouched = toNum(s.whTouchedCount)
  const nearExpire = toNum(s.nearExpireCount)
  const totalClaimed = adminClaimed + whClaimed
  const totalTouched = adminTouched + whTouched
  const slaRateStr = totalClaimed > 0 ? fmtPct(totalTouched, totalClaimed) : '100.0%'

  const slaCard = {
    key: 'sla',
    title: `认领 ${slaHours}h 首触达履约率`,
    primaryText: slaRateStr,
    badgeText: nearExpire > 0 ? `⚠️ ${nearExpire} 户临期弹回` : '保活健康',
    badgeType: nearExpire > 0 ? 'warning' : 'success',
    footText: `内部专员 ${fmtInt(adminTouched)}/${fmtInt(adminClaimed)} · 云仓认领 ${fmtInt(whTouched)}/${fmtInt(whClaimed)}（日限${whDailyCap}/在途${whActiveCap}户）`,
    question: `认领后的公海客户是否在 ${slaHours} 小时内完成了首通有效核实？是否存在占坑不跟进即将被自动回收的客户？`,
    logicNote: `认领后启动 ${slaHours}h 倒计时（biz_setting.sea_sla_hours）：未登记首通核实纪要（first_touched_at IS NULL）满期由 MktPublicSeaSlaJob 每小时自动弹回公海。`,
    rows: [
      {
        key: 'sla-admin',
        label: '园区内部招商专员首触达履约',
        value: `${fmtInt(adminTouched)} / ${fmtInt(adminClaimed)} 户 (${adminClaimed > 0 ? fmtPct(adminTouched, adminClaimed) : '100.0%'})`,
        verdict: nearExpire > 0 ? `含临近 12h 内即将超时弹回预警客户，请尽快补录首通纪要` : '在途专员认领单均已按时跟进或处于安全窗口',
        evidence: {
          rawTable: `adminClaimedTotal=${adminClaimed}, adminTouchedCount=${adminTouched}, nearExpireCount=${nearExpire}`,
          sourceNote: `crm_public_sea_meta (claim_status='CLAIMED_ADMIN') · ${fetchedAt}`,
          formula: adminClaimed > 0 ? `专员履约率 = ${adminTouched} ÷ ${adminClaimed} × 100% = ${fmtPct(adminTouched, adminClaimed)}` : '当前无在途专员认领单',
          actionLabel: '筛选查看「专员跟进中」客户',
          filterClaimStatus: 'CLAIMED_ADMIN',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'sla-wh',
        label: '加盟云仓主账号限时认领履约',
        value: `${fmtInt(whTouched)} / ${fmtInt(whClaimed)} 户 (${whClaimed > 0 ? fmtPct(whTouched, whClaimed) : '100.0%'})`,
        verdict: `配额硬护栏：单仓每日限领 ${whDailyCap} 户 · 在途未签约上限 ${whActiveCap} 户`,
        evidence: {
          rawTable: `whClaimedTotal=${whClaimed}, whTouchedCount=${whTouched}, sea_wh_daily_cap=${whDailyCap}, sea_wh_active_cap=${whActiveCap}`,
          sourceNote: `crm_public_sea_meta (claim_status='CLAIMED_WH') · ${fetchedAt}`,
          formula: totalClaimed > 0 ? `综合履约率 = (${adminTouched} + ${whTouched}) ÷ (${adminClaimed} + ${whClaimed}) × 100% = ${slaRateStr}` : null,
          actionLabel: '前往「规则与字典」调整保活时限与云仓配额',
          toPath: '/crm/marketing/setting',
          aiNote: '规则直算 · 零模型调用'
        }
      }
    ]
  }

  return [poolCard, washCard, slaCard]
}
