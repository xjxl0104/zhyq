import { money } from './feePeriod'
import { AUTO_FIT_PRINT_SCRIPT, renderSealOverlay } from './noticePrint'
import { LEASE_NOTICE_CSS } from './leaseNoticePrint'

const esc = (v) => String(v ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))
const br = (v) => esc(v).replace(/\n/g, '<br>')
const n2 = (v) => (v == null || v === '' ? '' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }))
const n4 = (v) => (v == null || v === '' ? '' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 4 }))
const dateCn = (s) => {
  if (!s) return ''
  const [y, m, d] = String(s).split('-')
  return `${y}年${Number(m)}月${Number(d)}日`
}

export function depositNoticeHtml(config, notice, sealPlacement = null) {
  const effCfg = notice?.headerConfig || config || {}
  const isRent = notice.feeCategory === 'RENT'
  const categoryTitle = isRent ? '租金保证金' : '物业保证金'
  const payeeName = notice.payeeName || (isRent ? effCfg?.rentPayeeName : effCfg?.propertyPayeeName) || ''
  const paymentAccount = notice.paymentAccount || (isRent ? effCfg?.rentPaymentAccount : effCfg?.propertyPaymentAccount) || ''
  const contactName = isRent ? effCfg?.rentContactName : effCfg?.propertyContactName
  const contactPhone = isRent ? effCfg?.rentContactPhone : effCfg?.propertyContactPhone

  const rows = (notice.items || []).map((it) => `<tr>
    <td class="c">${esc(it.seq)}</td>
    <td>${esc(it.feeItem)}</td>
    <td>${esc(it.unitName || '')}</td>
    <td class="r">${n2(it.leaseArea)}</td>
    <td class="r">${it.unitPrice == null ? '' : n4(it.unitPrice)}</td>
    <td class="r">${it.monthlyAmount == null ? '' : money(it.monthlyAmount)}</td>
    <td class="c">${esc(it.standardDesc || '2个月')}</td>
    <td class="r"><strong>${money(it.depositDue)}</strong></td>
    <td class="r">${money(it.depositPaid)}</td>
    <td class="r"><strong>${money(it.depositUnpaid)}</strong></td>
    <td class="remark">${esc(it.remark || '')}</td>
  </tr>`).join('')

  const notes = isRent
    ? [
        '1. 本通知单仅列明【租金保证金】（按合同2个月租金标准核定），请务必汇入本单指定的【租金保证金收取单位】账户，切勿与物业保证金混淆汇款。',
        '2. 保证金为合同履约一次性押金，不单独开具税务发票；到账后由收取单位开具保证金收据，合同期满无违约全额无息退还。',
        '3. 请按合同约定节点完成付款，并将汇款回单发送至园区财务对接人登记归档。'
      ]
    : [
        '1. 本通知单仅列明【物业管理费保证金】（按合同2个月物业管理费标准核定），请务必汇入本单指定的【物业保证金收取单位】账户，切勿与租金保证金混淆汇款。',
        '2. 保证金为物业服务履约一次性押金，不单独开具税务发票；到账后由收取单位开具保证金收据，合同期满无违约全额无息退还。',
        '3. 请按合同约定节点完成付款，并将汇款回单发送至物业财务对接人登记归档。'
      ]

  return `<section class="notice">
    <h1>${esc(effCfg?.parkName || 'DIPARK数智云仓产业园')} ${categoryTitle}缴费通知单</h1>
    <p class="sub">收取单位：<strong>${esc(payeeName || '待配置')}</strong>　｜　费用类别：${categoryTitle}（一次性履约保证金）</p>
    <table class="head">
      <tr><th>收取单位</th><td><strong>${esc(payeeName)}</strong></td><th>租户/公司名称</th><td>${esc(notice.tenantName)}</td><th>单据编号</th><td>${esc(notice.noticeNo)}</td></tr>
      <tr><th>楼层/区域</th><td>${esc(notice.unitName)}</td><th>对应协议编号</th><td>${esc(notice.agreementNo || '-')}</td><th>计租面积合计</th><td>${n2(notice.leaseArea)} ㎡</td></tr>
      <tr><th>开具日期</th><td>${dateCn(effCfg?.issueDate) || '-'}</td><th>缴费截止日</th><td>${dateCn(effCfg?.dueDate) || '按合同约定'}</td><th>联系人/电话</th><td>${esc([contactName, contactPhone].filter(Boolean).join(' / ') || '-')}</td></tr>
      <tr><th>收款账户/方式</th><td colspan="5">${br(paymentAccount || '-')}</td></tr>
    </table>
    <table class="items">
      <thead>
        <tr>
          <th>序号</th><th>费用项目</th><th>楼层/区域</th><th>计租面积(㎡)</th>
          <th>月单价(元/㎡/月)</th><th>月标准费(元)</th><th>保证金标准</th>
          <th>应收保证金(元)</th><th>已缴金额(元)</th><th>待缴余额(元)</th><th>缴纳约定/备注</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
    <div class="foot-block">
      <table class="foot">
        <tr><td class="note" rowspan="3"><b>收费说明（请核对收款单位）</b><br>${notes.map(esc).join('<br>')}</td><th>应收保证金合计</th><td class="r">${money(notice.totalDue)}</td><td>${categoryTitle}合计</td></tr>
        <tr><th>已缴保证金</th><td class="r">${money(notice.totalPaid)}</td><td>-</td></tr>
        <tr><th>本期应付余额</th><td class="r total">${money(notice.totalUnpaid)}</td><td></td></tr>
      </table>
      <div class="sign"><span>收取单位确认（签字/盖章）：${esc(payeeName)}</span><span>租户确认（签字/盖章）：</span></div>
    </div>
    ${renderSealOverlay(sealPlacement, 273)}
  </section>`
}

export function depositPrintTitle(notices) {
  const list = Array.isArray(notices) ? notices : []
  const cats = new Set(list.map((n) => n?.feeCategory).filter(Boolean))
  const catTitle = cats.size === 1
    ? (cats.has('RENT') ? '租金保证金缴费通知单' : '物业保证金缴费通知单')
    : '保证金缴费通知单'
  if (list.length === 1 && list[0]?.tenantName) {
    return `${list[0].tenantName}_${catTitle}`
  }
  return `${catTitle}汇总`
}

export function printDepositNotices(config, notices, autoPrint = true, sealMap = null) {
  const win = window.open('', '_blank')
  if (!win) return false
  const body = notices.map((n) => {
    const placement = sealMap ? (n.feeCategory === 'RENT' ? sealMap.RENT : sealMap.PROPERTY) : null
    return depositNoticeHtml(config, n, placement)
  }).join('')
  const title = depositPrintTitle(notices)
  win.document.write(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>${esc(title)}</title><style>${LEASE_NOTICE_CSS}</style></head><body>${body}${AUTO_FIT_PRINT_SCRIPT}</body></html>`)
  win.document.close()
  if (autoPrint) {
    win.focus()
    setTimeout(() => win.print(), 350)
  }
  return true
}
