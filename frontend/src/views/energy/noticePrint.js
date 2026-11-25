import { money, periodRange } from './feePeriod'

const NOTES = [
  '1. 水费、电费计费数量＝本期读数－上期读数；如存在倍率、损耗或公摊，请在备注中说明。',
  '2. 工业垃圾清运费应按租赁合同、园区收费标准或实际清运凭据填写。',
  '3. 本明细表为费用核对及收款依据之一；税务发票或收据的开具方式，以双方合同约定及适用税务规定为准。'
]

const esc = (v) => String(v ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))
const br = (v) => esc(v).replace(/\n/g, '<br>')
const n2 = (v) => (v == null || v === '' ? '' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }))
const n4 = (v) => (v == null || v === '' ? '' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 4 }))
const dateCn = (s) => {
  if (!s) return ''
  const [y, m, d] = s.split('-')
  return `${y}年${Number(m)}月${Number(d)}日`
}

/** 根据模板印章排版配置渲染绝对定位的电子印章 */
export function renderSealOverlay(sealPlacement, pageWidthMm = 273) {
  if (!sealPlacement || Number(sealPlacement.enabled) !== 1 || !sealPlacement.sealImageData) return ''
  const baseMm = Number(sealPlacement.defaultWidthMm || 44)
  const scale = Number(sealPlacement.scale || 1)
  const widthPct = ((baseMm / pageWidthMm) * 100 * scale).toFixed(2)
  const x = Number(sealPlacement.posX ?? 20).toFixed(2)
  const y = Number(sealPlacement.posY ?? 88).toFixed(2)
  const rot = Number(sealPlacement.rotation || 0).toFixed(1)
  const opacity = Number(sealPlacement.opacity ?? 0.92).toFixed(2)
  return `<img class="seal-stamp" src="${sealPlacement.sealImageData}" alt="电子印章" style="left:${x}%;top:${y}%;width:${widthPct}%;opacity:${opacity};transform:translate(-50%,-50%) rotate(${rot}deg);" />`
}

/** 一张通知单的 HTML,版式照线下表的缴费通知单 sheet */
export function noticeHtml(period, notice, sealPlacement = null) {
  const range = periodRange(period)
  const rows = (notice.items || []).map((it) => {
    const detail = /_DETAIL$/.test(it.itemType)
    return `<tr class="${detail ? 'detail' : ''}">
      <td class="c">${esc(it.seq)}</td>
      <td>${detail ? '　' : ''}${esc(it.feeItem)}</td>
      <td class="c">${esc(range)}</td>
      <td class="r">${n2(it.prevReading)}</td>
      <td class="r">${n2(it.currReading)}</td>
      <td class="r">${n4(it.quantity)}</td>
      <td class="c">${esc(it.unit)}</td>
      <td class="r">${it.unitPrice == null ? '' : n4(it.unitPrice)}</td>
      <td class="r">${detail || it.amount == null ? '' : money(it.amount)}</td>
      <td class="remark">${esc(it.remark)}</td>
    </tr>`
  }).join('')
  return `<section class="notice">
    <h1>${esc(period.parkName || '')}缴费通知单</h1>
    <p class="sub">本表用于向租户列明水电费及垃圾清运费；</p>
    <table class="head">
      <tr><th>园区名称</th><td>${esc(period.parkName)}</td><th>租户/公司名称</th><td>${esc(notice.tenantName)}</td><th>单据编号</th><td>${esc(notice.noticeNo)}</td></tr>
      <tr><th>厂房/单元</th><td>${esc(notice.unitName)}</td><th>收费期间</th><td>${esc(range)}</td><th>缴费截止日</th><td>${dateCn(period.dueDate)}</td></tr>
      <tr><th>联系人</th><td>${esc(period.contactName)}</td><th>联系电话</th><td>${esc(period.contactPhone)}</td><th>收款账户/方式</th><td>${br(period.paymentAccount)}</td></tr>
      <tr><th>开具日期</th><td>${dateCn(period.issueDate)}</td><th>备注</th><td colspan="3">${esc(notice.remark)}</td></tr>
    </table>
    <table class="items">
      <thead><tr><th>序号</th><th>费用项目</th><th>计费期间</th><th>上期读数</th><th>本期读数</th><th>计费数量</th><th>单位</th><th>单价（元）</th><th>金额（元）</th><th>备注</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>
    <table class="foot">
      <tr><td class="note" rowspan="4"><b>收费说明</b><br>${NOTES.map(esc).join('<br>')}</td><th>费用小计</th><td class="r">${money(notice.subtotal)}</td><td>本期应收合计</td></tr>
      <tr><th>已付金额</th><td class="r">${money(notice.paidAmount)}</td><td>-</td></tr>
      <tr><th>前期欠费</th><td class="r">${money(notice.priorArrears)}</td><td>${Number(notice.priorArrears || 0) ? '' : '无'}</td></tr>
      <tr><th>本期应收合计</th><td class="r total">${money(notice.totalDue)}</td><td></td></tr>
    </table>
    <div class="sign"><span>园区方确认（签字/盖章）：</span><span>租户确认（签字/盖章）：</span></div>
    ${renderSealOverlay(sealPlacement, 273)}
  </section>`
}

export const NOTICE_CSS = `
  * { box-sizing: border-box; }
  body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; font-size: 12px; color: #111; margin: 0; padding: 16px; }
  .notice { position: relative; page-break-after: always; max-width: 1040px; margin: 0 auto 32px; }
  .notice:last-child { page-break-after: auto; }
  .seal-stamp { position: absolute; z-index: 20; pointer-events: none; mix-blend-mode: multiply; height: auto; }
  h1 { text-align: center; font-size: 20px; margin: 0 0 4px; letter-spacing: 2px; }
  .sub { text-align: center; margin: 0 0 10px; color: #444; }
  table { width: 100%; border-collapse: collapse; margin-bottom: 8px; }
  th, td { border: 1px solid #333; padding: 5px 6px; vertical-align: middle; }
  th { background: #f3f3f3; font-weight: 600; text-align: center; white-space: nowrap; }
  .head th { width: 11%; }
  .head td { width: 22%; }
  .items td.c, .items th { text-align: center; }
  .items td.r { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
  .items td.remark { font-size: 11px; color: #333; }
  .items tr.detail td { color: #555; font-size: 11px; }
  .foot td.note { width: 62%; font-size: 11px; line-height: 1.6; vertical-align: top; }
  .foot th { width: 14%; }
  .foot td.r { text-align: right; width: 12%; font-variant-numeric: tabular-nums; }
  .foot td.total { font-weight: 700; font-size: 14px; }
  .sign { display: flex; justify-content: space-between; margin-top: 28px; padding: 0 8px; }
  @media print { body { padding: 0; } @page { size: A4 landscape; margin: 12mm; } }
`

/** 新开窗口打印:一张或全部(每张一页) */
export function printNotices(period, notices, autoPrint = true, sealPlacement = null) {
  const win = window.open('', '_blank')
  if (!win) return false
  const body = notices.map((n) => noticeHtml(period, n, sealPlacement)).join('')
  win.document.write(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>${esc(period.period)} 缴费通知单</title><style>${NOTICE_CSS}</style></head><body>${body}</body></html>`)
  win.document.close()
  if (autoPrint) {
    win.focus()
    setTimeout(() => win.print(), 300)
  }
  return true
}
