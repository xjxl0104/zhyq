// 内部专属公海池 4 张核心分析结论卡 —— 三层下钻（L1 结论 → L2 判据论证 → L3 五联物证）纯函数同源装配模块。
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

  // -------- 1. 公海有效活水池构成与广东热源占比（6源归集） --------
  const activeTotal = toNum(s.activeTotal)
  const calcHot = toNum(s.calcHotCount)
  const feiyuAd = toNum(s.feiyuAdCount)
  const amapPoi = toNum(s.amapPoiCount)
  const chanmama = toNum(s.chanmamaCount)
  const tungeeJob = toNum(s.tungeeJobCount)
  const expireReturn = toNum(s.expireReturnCount)
  const parkCrm = toNum(s.parkCrmCount)
  const hotRateStr = fmtPct(calcHot, activeTotal)
  const poolBadge = activeTotal === 0
    ? { text: '待补充数据源', type: 'info' }
    : calcHot > 0
      ? { text: `🔥 热单 ${hotRateStr}`, type: 'danger' }
      : { text: '供给正常', type: 'success' }

  const poolCard = {
    key: 'pool',
    title: '公海有效活水（6源归集）',
    primaryText: `${fmtInt(activeTotal)} 户`,
    badgeText: poolBadge.text,
    badgeType: poolBadge.type,
    footText: `算价/飞鱼 ${fmtInt(calcHot)} · 店铺榜 ${fmtInt(chanmama)} · 高德扫楼 ${fmtInt(amapPoi)} · 回流/调剂 ${fmtInt(tungeeJob + expireReturn + parkCrm)}`,
    question: '当前内部公海池的可跟进广东货主从哪 6 条低成本数据源管线归集而来？热询盘占比如何计算？',
    logicNote: '直接读取 crm_public_sea_meta 关联 crm_customer（referrer_id IS NULL 且 status=1 未归档冷宫）按 sea_source 同源计数；对外部 P1–P4 完全不可见。',
    rows: [
      {
        key: 'pool-hot',
        label: '🔥 30秒算价器留资 + 巨量飞鱼免费 Webhook',
        value: `${fmtInt(calcHot)} 户 (${hotRateStr})`,
        verdict: calcHot > 0 ? `最高转化热源（含飞鱼广告直推 ${fmtInt(feiyuAd)} 户）：货主刚测完底价或提交广东找仓表单` : '暂无线上热询盘，可复制飞鱼 Webhook 地址或投放 30 秒算价页截流',
        evidence: {
          rawTable: `calcHotCount=${calcHot} (含 FEIYU_AD=${feiyuAd}), activeTotal=${activeTotal}`,
          sourceNote: `/open/public-calc/lead + /open/v1/ad-leads/feiyu · 统计时间 ${fetchedAt}`,
          formula: activeTotal > 0 ? `热单占比 = ${calcHot} ÷ ${activeTotal} × 100% = ${hotRateStr}` : '分母 activeTotal = 0，占比记 0.0%',
          actionLabel: '筛选查看「30秒算价器留资」客户',
          filterSource: 'CALC_HOT',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'pool-chanmama',
        label: '🏆 类目 TOP500 店铺榜自动对齐 + 探迹招聘库',
        value: `${fmtInt(chanmama + tungeeJob)} 户`,
        verdict: `精准痛点源：类目榜单对齐 ${fmtInt(chanmama)} 户 + 正在招打包工特征库 ${fmtInt(tungeeJob)} 户`,
        evidence: {
          rawTable: `CHANMAMA=${chanmama}, TUNGEE_JOB=${tungeeJob}, activeTotal=${activeTotal}`,
          sourceNote: `Excel/JSON 防撞对齐入池 · 统计时间 ${fetchedAt}`,
          formula: activeTotal > 0 ? `榜单与招聘特征占比 = (${chanmama} + ${tungeeJob}) ÷ ${activeTotal} × 100% = ${fmtPct(chanmama + tungeeJob, activeTotal)}` : null,
          actionLabel: '筛选查看「榜单企查对齐」客户',
          filterSource: 'CHANMAMA',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'pool-amap',
        label: '📡 高德周边 10km 广东电商园 POI 扫楼雷达',
        value: `${fmtInt(amapPoi)} 户`,
        verdict: '免费配额挖掘园区周边直播基地、美妆园与服装城真实经营主体',
        evidence: {
          rawTable: `sea_source='AMAP_POI', count=${amapPoi}`,
          sourceNote: `高德开放平台 /v3/place/around 免费接口 · 统计时间 ${fetchedAt}`,
          formula: null,
          actionLabel: '筛选查看「高德POI扫楼」客户',
          filterSource: 'AMAP_POI',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'pool-return',
        label: '♻️ 外部伙伴超期回流 + 园区看房调剂',
        value: `${fmtInt(expireReturn + parkCrm)} 户`,
        verdict: `零成本盘活内部存量（伙伴超期回流 ${fmtInt(expireReturn)} 户 + 园区调剂 ${fmtInt(parkCrm)} 户）`,
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

  // -------- 2. 高德云仓 5km 黄金地推圈与高密写字楼聚集 --------
  const within5km = toNum(s.within5kmCount)
  const clusterBldgs = toNum(s.clusterBuildingCount)
  const zones = s.zoneCounts && typeof s.zoneCounts === 'object' ? s.zoneCounts : {}
  const gzCnt = toNum(zones.GZ)
  const dgCnt = toNum(zones.DG)
  const fsCnt = toNum(zones.FS)
  const szCnt = toNum(zones.SZ)
  const ydCnt = toNum(zones.YD)

  const mapCard = {
    key: 'map',
    title: '高德云仓 5km 黄金地推圈',
    primaryText: `${fmtInt(within5km)} 户`,
    badgeText: `🏢 ${fmtInt(clusterBldgs)} 栋聚集大厦`,
    badgeType: within5km > 0 ? 'primary' : 'info',
    footText: `广州 ${fmtInt(gzCnt)} · 东莞 ${fmtInt(dgCnt)} · 佛山 ${fmtInt(fsCnt)} · 深圳/粤东 ${fmtInt(szCnt + ydCnt)}`,
    question: '多少公海货主位于本园加盟云仓 5km（货车 15 分钟）黄金揽收圈内？哪些写字楼适合一次上门扫多户？',
    logicNote: '基于 crm_public_sea_meta 的 GCJ-02 坐标测算 dist_nearest_wh_km <= 5.0 黄金圈户数，并按 building_name 聚合识别同楼 >=2 户的电商大厦。',
    rows: [
      {
        key: 'map-5km',
        label: '⭕ 距本园加盟云仓 ≤5.0km 黄金地推圈货主',
        value: `${fmtInt(within5km)} 户 (${fmtPct(within5km, activeTotal)})`,
        verdict: '短驳揽收成本最低、老板现场看仓 15 分钟即达，外勤地推首优攻坚层',
        evidence: {
          rawTable: `within5kmCount=${within5km}, activeTotal=${activeTotal}`,
          sourceNote: `高德地理编码 GCJ-02 坐标球面折算距离 · ${fetchedAt}`,
          formula: activeTotal > 0 ? `5km黄金圈占比 = ${within5km} ÷ ${activeTotal} × 100% = ${fmtPct(within5km, activeTotal)}` : '当前公海户数为 0',
          actionLabel: '一键切至「高德地推地图」查看 5km 圈',
          switchMap5km: true,
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'map-cluster',
        label: '🏢 同栋电商大厦/产业园多户聚集点',
        value: `${fmtInt(clusterBldgs)} 栋大厦`,
        verdict: '同一栋大厦或直播基地聚集 ≥2 家待开发货主，专员进一趟楼可连访多户',
        evidence: {
          rawTable: `clusterBuildingCount=${clusterBldgs}, zoneCounts={GZ:${gzCnt}, DG:${dgCnt}, FS:${fsCnt}, SZ:${szCnt}, YD:${ydCnt}}`,
          sourceNote: `crm_public_sea_meta.building_name GROUP BY HAVING COUNT>=2 · ${fetchedAt}`,
          formula: null,
          actionLabel: '筛选查看「同栋大厦聚集」货主',
          filterDist: 'CLUSTER',
          aiNote: '规则直算 · 零模型调用'
        }
      }
    ]
  }

  // -------- 3. 工商网店穿透、三道防撞闸与广东省钱锁审计 --------
  const totalShops = toNum(s.totalLinkedShops)
  const dishonestRisk = toNum(s.dishonestRiskCount)
  const washTotal = toNum(s.washTotalRows)
  const washInserted = toNum(s.washInsertedRows)
  const blkPrivate = toNum(s.blockedPrivateLock)
  const blkHmac = toNum(s.blockedWhHmac)
  const blkAgency = toNum(s.blockedDupAgency)
  const skippedOut = toNum(s.skippedOutProvince)
  const cacheHits = toNum(s.qccCacheHits)
  const apiCalls = toNum(s.qccApiCalls)
  const totalBlocked = blkPrivate + blkHmac + blkAgency
  const blockRateStr = fmtPct(totalBlocked, washTotal)

  const qccCard = {
    key: 'qcc',
    title: '工商网店穿透 & 省钱防撞闸',
    primaryText: `${fmtInt(totalShops)} 店`,
    badgeText: dishonestRisk > 0 ? `⚡ 排雷 ${dishonestRisk} 户失信` : `防撞拦截 ${blockRateStr}`,
    badgeType: dishonestRisk > 0 ? 'danger' : 'success',
    footText: `外省锁跳过 ${fmtInt(skippedOut)} 行 · 命中30天缓存 ${fmtInt(cacheHits)} 次 · 三道闸护客 ${fmtInt(totalBlocked)} 行`,
    question: '类目榜单导入时，广东定向省钱锁与 30 天本地缓存省下了多少次外部 API 扣费？三道防撞闸拦截了多少冲突与失信主体？',
    logicNote: '汇总 crm_public_sea_shop 1客多店矩阵、crm_public_sea_meta 失信预警及近 30 天 crm_public_sea_wash_batch 洗数批次台账。',
    rows: [
      {
        key: 'qcc-lock-cache',
        label: '💰 广东定向省钱锁 + 30天本地工商缓存复用',
        value: `免扣费 ${fmtInt(skippedOut + cacheHits)} 次`,
        verdict: `外省主体自动跳过 ${fmtInt(skippedOut)} 行 + 30天缓存命中 ${fmtInt(cacheHits)} 次（实际外发 API ${fmtInt(apiCalls)} 次）`,
        evidence: {
          rawTable: `skippedOutProvince=${skippedOut}, qccCacheHits=${cacheHits}, qccApiCalls=${apiCalls}`,
          sourceNote: `MktExtDataGateway + crm_ext_company_cache · ${fetchedAt}`,
          formula: `免扣费拦截合计 = 外省跳过(${skippedOut}) + 本地缓存命中(${cacheHits}) = ${skippedOut + cacheHits} 次`,
          actionLabel: '打开「类目TOP500店铺榜对齐导入」',
          openImport: true,
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'qcc-wash',
        label: '🛡️ 入池三道防撞闸（护私海 · 护云仓HMAC老客 · 剔代账号）',
        value: `拦截 ${fmtInt(totalBlocked)} 行 (${blockRateStr})`,
        verdict: `近30天洗数 ${fmtInt(washTotal)} 行 → 护私海 ${fmtInt(blkPrivate)} · 护云仓老客 ${fmtInt(blkHmac)} · 剔代账号 ${fmtInt(blkAgency)} · 净入 ${fmtInt(washInserted)} 户`,
        evidence: {
          rawTable: `washTotal=${washTotal}, blockedPrivate=${blkPrivate}, blockedHmac=${blkHmac}, blockedAgency=${blkAgency}, inserted=${washInserted}`,
          sourceNote: `crm_public_sea_wash_batch 近30天审计汇总 · ${fetchedAt}`,
          formula: washTotal > 0 ? `三道闸综合拦截率 = (${blkPrivate} + ${blkHmac} + ${blkAgency}) ÷ ${washTotal} × 100% = ${blockRateStr}` : '近30天暂无导入批次，记 0.0%',
          actionLabel: '前往「客户管理」核对在锁归属',
          toPath: '/crm/marketing/customer',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'qcc-risk',
        label: '⚡ 失信被执行 / 限高老赖主体签约前排雷',
        value: `${fmtInt(dishonestRisk)} 户预警`,
        verdict: dishonestRisk > 0 ? '已自动标记红牌预警：仅允许预充值发货，严禁开放月结账期' : '当前在池主体均未命中失信被执行记录',
        evidence: {
          rawTable: `dishonestRiskCount=${dishonestRisk}, totalLinkedShops=${totalShops}`,
          sourceNote: `crm_public_sea_meta.dishonest_count > 0 · ${fetchedAt}`,
          formula: null,
          actionLabel: dishonestRisk > 0 ? '筛选查看「失信预警」风险主体' : null,
          filterRisk: dishonestRisk > 0 ? 'RISK' : null,
          aiNote: '规则直算 · 零模型调用'
        }
      }
    ]
  }

  // -------- 4. 72h 电销首触达 & 500m 拍照打卡延保 15 天履约率 --------
  const slaHours = toNum(s.slaHours, 72)
  const whDailyCap = toNum(s.whDailyCap, 5)
  const whActiveCap = toNum(s.whActiveCap, 10)
  const checkinMaxM = toNum(s.checkinMaxMeters, 500)
  const protectDays = toNum(s.visitProtectDays, 15)
  const visitProtected = toNum(s.visitProtectedCount)
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
    title: `${slaHours}h 电销 & ${checkinMaxM}m 打卡保活率`,
    primaryText: slaRateStr,
    badgeText: visitProtected > 0 ? `📍 ${visitProtected}户延保${protectDays}天` : (nearExpire > 0 ? `⚠️ ${nearExpire} 户临期弹回` : '保活健康'),
    badgeType: nearExpire > 0 ? 'warning' : 'success',
    footText: `专员 ${fmtInt(adminTouched)}/${fmtInt(adminClaimed)} · 云仓 ${fmtInt(whTouched)}/${fmtInt(whClaimed)}（日限${whDailyCap}/在途${whActiveCap}户）`,
    question: `认领后的客户是否在 ${slaHours}h 内完成电销首触达？有多少户通过现场 ≤${checkinMaxM}m 拍照打卡获得了 ${protectDays} 天深度地推保护？`,
    logicNote: `电销认领启动 ${slaHours}h 倒计时；外勤到达客户坐标 ≤${checkinMaxM}m 实拍门头打卡后，自动写入 crm_public_sea_checkin 并解锁 ${protectDays} 天地推保护期。`,
    rows: [
      {
        key: 'sla-visit',
        label: `📍 外勤现场 ≤${checkinMaxM}m GPS 实拍门头打卡延保`,
        value: `${fmtInt(visitProtected)} 户保护中`,
        verdict: `球面测距 Haversine ≤${checkinMaxM}m 严防异地虚拟打卡，通过后享 ${protectDays} 天深度跟进保护`,
        evidence: {
          rawTable: `visitProtectedCount=${visitProtected}, sea_checkin_max_meters=${checkinMaxM}, sea_visit_protect_days=${protectDays}`,
          sourceNote: `crm_public_sea_checkin 审计留痕表 · ${fetchedAt}`,
          formula: `实测偏差米数 ≤ ${checkinMaxM}m 且含门头实拍照 → visit_protected_until = NOW() + ${protectDays} 天`,
          actionLabel: '筛选查看「15天地推保护中」客户',
          filterClaimStatus: 'VISIT_15D',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'sla-admin-wh',
        label: `⏱ 电销认领 ${slaHours}h 首触达与云仓配额履约`,
        value: `${fmtInt(totalTouched)} / ${fmtInt(totalClaimed)} 户 (${slaRateStr})`,
        verdict: nearExpire > 0
          ? `含 ${nearExpire} 户距超时不足 12h 预警客户；云仓配额日限 ${whDailyCap} / 在途 ${whActiveCap} 户`
          : `专员 ${fmtInt(adminTouched)}/${fmtInt(adminClaimed)} · 云仓 ${fmtInt(whTouched)}/${fmtInt(whClaimed)}（日限${whDailyCap}/在途${whActiveCap}户）`,
        evidence: {
          rawTable: `admin=${adminTouched}/${adminClaimed}, wh=${whTouched}/${whClaimed}, nearExpire=${nearExpire}`,
          sourceNote: `crm_public_sea_meta + MktPublicSeaSlaJob · ${fetchedAt}`,
          formula: totalClaimed > 0 ? `综合首触达率 = (${adminTouched} + ${whTouched}) ÷ (${adminClaimed} + ${whClaimed}) × 100% = ${slaRateStr}` : '当前无在途认领单，记 100.0%',
          actionLabel: '前往「规则与字典」调整保活与外部连接器参数',
          toPath: '/crm/marketing/setting',
          aiNote: '规则直算 · 零模型调用'
        }
      }
    ]
  }

  return [poolCard, mapCard, qccCard, slaCard]
}
