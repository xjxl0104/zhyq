// 7大域 18 通道外部数据连接器中心 —— 3 张分析结论卡三层下钻（L1 -> L2 -> L3）纯函数同源装配模块。
// 严格对齐 MktConnectorHubController.overview 返回的 summary 同源字段，抽屉内零二次重算。

function toNum(v, def = 0) {
  const n = Number(v)
  return Number.isFinite(n) ? n : def
}

function fmtInt(v) {
  return Math.trunc(toNum(v, 0)).toLocaleString('zh-CN')
}

export function buildConnectorHubDrilldownCards(rawSummary = {}) {
  const s = rawSummary && typeof rawSummary === 'object' ? rawSummary : {}
  const fetchedAt = s.fetchedAt || '实时 SQL 同源聚合（/crm/marketing/connectors/overview）'

  const total = toNum(s.totalConnectors, 18)
  const active = toNum(s.activeConnectors, 0)
  const freeCnt = toNum(s.freeConnectors, 8)
  const paidCnt = toNum(s.paidConnectors, 10)
  const fusedCnt = toNum(s.fusedConnectors, 0)

  const hubCard = {
    key: 'hub',
    title: '连接器热插拔点亮总览',
    primaryText: `${fmtInt(active)} / ${fmtInt(total)} 通道`,
    badgeText: active > 0 ? '按需运行中' : '全 ¥0 待机中',
    badgeType: active > 0 ? 'success' : 'info',
    footText: `免费/官方通道 ${fmtInt(freeCnt)} 个 · 商业按次/SaaS通道 ${fmtInt(paidCnt)} 个（未配Key零扣费）`,
    question: `全部 ${total} 条外部接口如何做到「代码一次性全量就位、未买套餐零月租待机、随填随亮」？`,
    logicNote: '直接读取 crm_ext_connector_config 同源配置：enabled=0 的通道不发起任何外部 HTTP 请求，保持 ¥0 待机。',
    rows: [
      {
        key: 'hub-free',
        label: '🟢 8 条 ¥0 免费与官方开放平台通道',
        value: `${fmtInt(freeCnt)} 条`,
        verdict: '含高德全套LBS、巨量飞鱼/腾讯线索通/小红书聚光/百度基木鱼4大Webhook、企微机器人、抖店与拼多多官方验店',
        evidence: {
          rawTable: `freeConnectors=${freeCnt}, activeConnectors=${active}, totalConnectors=${total}`,
          sourceNote: `crm_ext_connector_config (fee_tier='FREE') · ${fetchedAt}`,
          formula: `点亮覆盖率 = ${active} ÷ ${total} × 100% = ${total > 0 ? ((active / total) * 100).toFixed(1) : '0.0'}%`,
          filterCat: 'AD',
          actionLabel: '筛选查看「四大免费投流 Webhook」',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'hub-paid',
        label: '🟡 10 条商业付费 / 按次资源包通道（随申随亮）',
        value: `${fmtInt(paidCnt)} 条`,
        verdict: '含企查查、天眼查、百元工商包、探迹、蝉妈妈、店透巢、淘宝验店、快递100、隐私小号、6%专票验真',
        evidence: {
          rawTable: `paidConnectors=${paidCnt}, standby=${Math.max(0, total - active)}`,
          sourceNote: `MktConnectorHubService.DEFAULT_SEEDS · ${fetchedAt}`,
          formula: null,
          openBatchSheet: true,
          actionLabel: '一键导出「同事分批申领任务单」',
          aiNote: '规则直算 · 零模型调用'
        }
      }
    ]
  }

  const outGd = toNum(s.blockedOutGdTotal)
  const cacheHits = toNum(s.cacheHitTotal)
  const monthUsed = toNum(s.monthUsedTotal)
  const savedYuan = Number(s.estimatedSavedYuan || 0).toFixed(2)

  const costCard = {
    key: 'cost',
    title: '省钱护栏与月度限额熔断',
    primaryText: `省 ¥${savedYuan}`,
    badgeText: fusedCnt > 0 ? `⚠️ ${fusedCnt} 通道达限额熔断` : '0 超支风险',
    badgeType: fusedCnt > 0 ? 'danger' : 'primary',
    footText: `外省锁跳过 ${fmtInt(outGd)} 次 · 30天缓存复用 ${fmtInt(cacheHits)} 次 · 本月实调 ${fmtInt(monthUsed)} 次`,
    question: '点亮商业付费接口后，哪三道硬护栏确保不会因外省名单或脚本异常刷爆接口账单？',
    logicNote: '同源汇总连接器与洗数批次的外省跳过数、30天缓存命中数及当月调用额度熔断状态。',
    rows: [
      {
        key: 'cost-gd-cache',
        label: '💰 广东省定向硬锁 + 30天本地缓存免扣费拦截',
        value: `免扣 ${fmtInt(outGd + cacheHits)} 次`,
        verdict: `外省主体自动拦截 ${fmtInt(outGd)} 次 + 30天缓存直接命中 ${fmtInt(cacheHits)} 次`,
        evidence: {
          rawTable: `blockedOutGdTotal=${outGd}, cacheHitTotal=${cacheHits}, estimatedSavedYuan=${savedYuan}`,
          sourceNote: `crm_ext_connector_config + crm_public_sea_wash_batch · ${fetchedAt}`,
          formula: `折算节约费用 = (${outGd} + ${cacheHits}) × ¥0.20/次 = ¥${savedYuan}`,
          filterCat: 'CORP',
          actionLabel: '查看「工商与手机穿透」省钱锁配置',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'cost-fuse',
        label: '🛡️ 单通道月度调用上限自动熔断阀',
        value: fusedCnt > 0 ? `${fusedCnt} 通道已护账熔断` : `本月已用 ${fmtInt(monthUsed)} 次`,
        verdict: '任一付费连接器当月达到 monthly_quota_limit 时自动暂停外发 HTTP 请求，零超支可能',
        evidence: {
          rawTable: `monthUsedTotal=${monthUsed}, fusedConnectors=${fusedCnt}`,
          sourceNote: `MktConnectorHubService.acquireActiveCredentials · ${fetchedAt}`,
          formula: `当 month_used_count >= monthly_quota_limit 时自动置为 QUOTA_FUSED`,
          actionLabel: null,
          aiNote: '规则直算 · 零模型调用'
        }
      }
    ]
  }

  const adInbox = toNum(s.adInboxTotal)
  const shopVer = toNum(s.shopVerifiedCount)
  const wbChecked = toNum(s.waybillCheckedCount)
  const wbFake = toNum(s.waybillFakeCount)
  const invVer = toNum(s.invoiceVerifiedCount)

  const guardCard = {
    key: 'guard',
    title: '全链路进线与防刷验票闭环',
    primaryText: `${fmtInt(adInbox + shopVer + wbChecked + invVer)} 笔留痕`,
    badgeText: wbFake > 0 ? `🚨 抓获 ${wbFake} 笔异常空包` : '获客→验店→抽检→验票',
    badgeType: wbFake > 0 ? 'danger' : 'success',
    footText: `广告推流 ${fmtInt(adInbox)} · 扫码验店 ${fmtInt(shopVer)} · 运单抽检 ${fmtInt(wbChecked)} · 专票验真 ${fmtInt(invVer)}`,
    question: '从全域广告获客、大客真实单量核验，到月度运单防刷与 P4 企业提现验票，各环节累计沉淀了多少真实核验记录？',
    logicNote: '同源汇总 crm_ext_ad_lead_inbox、crm_shop_oauth_session、crm_waybill_spot_check 与 crm_invoice_verify_log 四张审计表。',
    rows: [
      {
        key: 'guard-front',
        label: '📣 全域投流进线 + 10秒官方扫码验店真单量',
        value: `推流 ${fmtInt(adInbox)} / 验店 ${fmtInt(shopVer)}`,
        verdict: '飞鱼/腾讯/小红书/百度 0秒幂等入池，抖店/淘系/拼多多核验近30天真单量防虚报压价',
        evidence: {
          rawTable: `adInboxTotal=${adInbox}, shopVerifiedCount=${shopVer}`,
          sourceNote: `crm_ext_ad_lead_inbox + crm_shop_oauth_session · ${fetchedAt}`,
          formula: null,
          filterCat: 'ECOM',
          actionLabel: '筛选查看「电商榜单与官方验店」通道',
          aiNote: '规则直算 · 零模型调用'
        }
      },
      {
        key: 'guard-back',
        label: '📦 快递100 2%轨迹防刷 + 🧾 6%增值税专票验真',
        value: `抽检 ${fmtInt(wbChecked)} / 验票 ${fmtInt(invVer)}`,
        verdict: wbFake > 0 ? `已拦截 ${wbFake} 笔无真实揽收轨迹的可疑运单！` : '严守出库真实履约与企业公户合规专票结算底线',
        evidence: {
          rawTable: `waybillCheckedCount=${wbChecked}, waybillFakeCount=${wbFake}, invoiceVerifiedCount=${invVer}`,
          sourceNote: `crm_waybill_spot_check + crm_invoice_verify_log · ${fetchedAt}`,
          formula: null,
          filterCat: 'LOGI',
          actionLabel: '筛选查看「快递100防刷抽检」通道',
          aiNote: '规则直算 · 零模型调用'
        }
      }
    ]
  }

  return [hubCard, costCard, guardCard]
}
