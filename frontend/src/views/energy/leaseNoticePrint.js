import { money, periodRange } from './feePeriod'
import { renderSealOverlay } from './noticePrint'

const esc = (v) => String(v ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))
const br = (v) => esc(v).replace(/\n/g, '<br>')
const n2 = (v) => (v == null || v === '' ? '' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }))
const n4 = (v) => (v == null || v === '' ? '' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 4 }))
const dateCn = (s) => {
  if (!s) return ''
  const [y, m, d] = String(s).split('-')
  return `${y}年${Number(m)}月${Number(d)}日`
}
const factorText = (v) => {
  if (v == null || v === '') return ''
  const n = Number(v)
  if (n === 1) return '100%（全额）'
  if (n === 0) return '0（免缴）'
  if (Math.abs(n - 0.5) < 0.0001) return '0.5（5折）'
  if (Math.abs(n - 0.7) < 0.0001) return '0.7（7折）'
  return `${Number(n.toFixed(4))}`
}

export function leaseNoticeHtml(period, notice, sealPlacement = null) {
  const isRent = notice.feeCategory === 'RENT'
  const categoryTitle = isRent ? '租金' : '物业管理费'
  const payeeName = notice.payeeName || (isRent ? period.rentPayeeName : period.propertyPayeeName) || ''
  const paymentAccount = notice.paymentAccount || (isRent ? period.rentPaymentAccount : period.propertyPaymentAccount) || ''
  const contactName = isRent ? period.rentContactName : period.propertyContactName
  const contactPhone = isRent ? period.rentContactPhone : period.propertyContactPhone
  const range = periodRange(period)

  const rows = (notice.items || []).map((it) => `<tr>
    <td class="c">${esc(it.seq)}</td>
    <td>${esc(it.feeItem)}</td>
    <td>${esc(it.unitName || '')}</td>
    <td class="c">${esc(range)}</td>
    <td class="r">${n2(it.leaseArea)}</td>
    <td class="r">${it.unitPrice == null ? '' : n4(it.unitPrice)}</td>
    <td class="r">${it.standardAmount == null ? '' : money(it.standardAmount)}</td>
    <td class="c">${factorText(it.discountFactor)}</td>
    <td class="r"><strong>${it.amount == null ? '' : money(it.amount)}</strong></td>
    <td class="remark">${esc(it.remark)}</td>
  </tr>`).join('')

  const notes = isRent
    ? [
        '1. 本通知单仅列明本期房屋/厂房租金，请务必汇入本单指定的【租金收取单位】账户，切勿与物业管理费混淆汇款。',
        '2. 租金按合同约定面积 × 当期适用单价 × 减免系数计算；递增、免租及优惠期按双方租赁合同执行。',
        '3. 请于缴费截止日前完成付款，并将汇款回单发送至园区财务对接人，以便及时开具发票或收据。'
      ]
    : [
        '1. 本通知单仅列明本期物业管理费，请务必汇入本单指定的【物业管理费收取单位】账户，切勿与租金混淆汇款。',
        '2. 物业管理费按合同约定面积 × 当期适用单价 × 减免系数计算；递增与减免按物业服务合同执行。',
        '3. 请于缴费截止日前完成付款，并将汇款回单发送至物业财务对接人，以便及时开具发票或收据。'
      ]

  return `<section class="notice">
    <h1>${esc(period.parkName || '')} ${categoryTitle}缴费通知单</h1>
    <p class="sub">收取单位：<strong>${esc(payeeName || '待配置')}</strong>　｜　费用类别：${categoryTitle}</p>
    <table class="head">
      <tr><th>收取单位</th><td><strong>${esc(payeeName)}</strong></td><th>租户/公司名称</th><td>${esc(notice.tenantName)}</td><th>单据编号</th><td>${esc(notice.noticeNo)}</td></tr>
      <tr><th>楼层/区域</th><td>${esc(notice.unitName)}</td><th>收费期间</th><td>${esc(range)}</td><th>缴费截止日</th><td>${dateCn(period.dueDate)}</td></tr>
      <tr><th>对应协议编号</th><td>${esc(notice.agreementNo || '-')}</td><th>计租面积合计</th><td>${n2(notice.leaseArea)} ㎡</td><th>开具日期</th><td>${dateCn(period.issueDate)}</td></tr>
      <tr><th>联系人/电话</th><td>${esc([contactName, contactPhone].filter(Boolean).join(' / ') || '-')}</td><th>收款账户/方式</th><td colspan="3">${br(paymentAccount || '-')}</td></tr>
      ${notice.remark ? `<tr><th>备注</th><td colspan="5">${esc(notice.remark)}</td></tr>` : ''}
    </table>
    <table class="items">
      <thead>
        <tr>
          <th>序号</th><th>费用项目</th><th>楼层/区域</th><th>计费期间</th>
          <th>计租面积(㎡)</th><th>当期单价(元/㎡/月)</th><th>标准金额(元)</th>
          <th>减免系数</th><th>应付金额(元)</th><th>备注</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
    <table class="foot">
      <tr><td class="note" rowspan="4"><b>收费说明（请核对收款单位）</b><br>${notes.map(esc).join('<br>')}</td><th>费用小计</th><td class="r">${money(notice.subtotal)}</td><td>${categoryTitle}小计</td></tr>
      <tr><th>已付金额</th><td class="r">${money(notice.paidAmount)}</td><td>-</td></tr>
      <tr><th>前期欠费</th><td class="r">${money(notice.priorArrears)}</td><td>${Number(notice.priorArrears || 0) ? '' : '无'}</td></tr>
      <tr><th>本期应收合计</th><td class="r total">${money(notice.totalDue)}</td><td></td></tr>
    </table>
    <div class="sign"><span>收取单位确认（签字/盖章）：${esc(payeeName)}</span><span>租户确认（签字/盖章）：</span></div>
    ${renderSealOverlay(sealPlacement, 273)}
  </section>`
}

export const LEASE_NOTICE_CSS = `
  * { box-sizing: border-box; }
  body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; font-size: 12px; color: #111; margin: 0; padding: 16px; }
  .notice { position: relative; page-break-after: always; max-width: 1080px; margin: 0 auto 32px; }
  .notice:last-child { page-break-after: auto; }
  .seal-stamp { position: absolute; z-index: 20; pointer-events: none; mix-blend-mode: multiply; height: auto; }
  h1 { text-align: center; font-size: 20px; margin: 0 0 4px; letter-spacing: 2px; }
  .sub { text-align: center; margin: 0 0 10px; color: #333; font-size: 13px; }
  table { width: 100%; border-collapse: collapse; margin-bottom: 8px; }
  th, td { border: 1px solid #333; padding: 5px 6px; vertical-align: middle; }
  th { background: #f3f3f3; font-weight: 600; text-align: center; white-space: nowrap; }
  .head th { width: 11%; }
  .head td { width: 22%; }
  .items td.c, .items th { text-align: center; }
  .items td.r { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
  .items td.remark { font-size: 11px; color: #333; }
  .foot td.note { width: 62%; font-size: 11px; line-height: 1.6; vertical-align: top; }
  .foot th { width: 14%; }
  .foot td.r { text-align: right; width: 12%; font-variant-numeric: tabular-nums; }
  .foot td.total { font-weight: 700; font-size: 14px; }
  .sign { display: flex; justify-content: space-between; margin-top: 28px; padding: 0 8px; }
  @media print { body { padding: 0; } @page { size: A4 landscape; margin: 12mm; } }
`

export function printLeaseNotices(period, notices, autoPrint = true, sealMap = null) {
  const win = window.open('', '_blank')
  if (!win) return false
  const body = notices.map((n) => {
    const placement = sealMap ? (n.feeCategory === 'RENT' ? sealMap.RENT : sealMap.PROPERTY) : null
    return leaseNoticeHtml(period, n, placement)
  }).join('')
  win.document.write(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>${esc(period.period)} 租金与物业管理费缴费通知单</title><style>${LEASE_NOTICE_CSS}</style></head><body>${body}</body></html>`)
  win.document.close()
  if (autoPrint) {
    win.focus()
    setTimeout(() => win.print(), 300)
  }
  return true
}
