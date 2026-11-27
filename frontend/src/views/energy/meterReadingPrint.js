const esc = (v) => String(v ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))
const fmtNum = (v, digits = 2) => {
  if (v == null || v === '') return ''
  const n = Number(v)
  if (!Number.isFinite(n)) return String(v)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: digits })
}

export const METER_READING_PRINT_CSS = `
  @page { size: A4 landscape; margin: 10mm 11mm; }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    color: #111;
    background: #fff;
    font: 12px/1.45 "PingFang SC", "Microsoft YaHei", "SimSun", sans-serif;
  }
  .reading-sheet { width: 100%; }
  .sheet-title {
    text-align: center;
    font-size: 19px;
    font-weight: 700;
    letter-spacing: 1px;
    margin: 0 0 4px;
  }
  .sheet-sub {
    text-align: center;
    font-size: 11px;
    color: #555;
    margin: 0 0 10px;
  }
  .meta-table {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: 10px;
  }
  .meta-table td, .meta-table th {
    border: 1px solid #333;
    padding: 4px 8px;
    font-size: 12px;
  }
  .meta-table th {
    background: #f4f6f9;
    font-weight: 600;
    text-align: center;
    white-space: nowrap;
    width: 90px;
  }
  .sec-head {
    margin: 10px 0 4px;
    font-size: 13px;
    font-weight: 700;
    color: #111;
    display: flex;
    justify-content: space-between;
    align-items: baseline;
  }
  .sec-head span { font-size: 11px; font-weight: 400; color: #555; }
  .grid-table {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: 10px;
  }
  .grid-table thead { display: table-header-group; }
  .grid-table tr { break-inside: avoid; page-break-inside: avoid; }
  .grid-table th, .grid-table td {
    border: 1px solid #333;
    padding: 5px 6px;
    font-size: 11.5px;
    vertical-align: middle;
  }
  .grid-table th {
    background: #f4f6f9;
    font-weight: 700;
    text-align: center;
    white-space: nowrap;
  }
  .grid-table td.c { text-align: center; }
  .grid-table td.r { text-align: right; font-variant-numeric: tabular-nums; }
  .grid-table td.write-box {
    min-width: 78px;
    height: 24px;
  }
  .grid-table td.sign-box {
    min-width: 82px;
  }
  .grid-table tr.prop-row td { background: #fafafa; }
  .notes-box {
    margin-top: 8px;
    padding: 6px 8px;
    border: 1px dashed #888;
    font-size: 11px;
    color: #333;
    line-height: 1.6;
    white-space: pre-wrap;
  }
  .sign-bar {
    margin-top: 14px;
    display: flex;
    justify-content: space-between;
    gap: 16px;
    font-size: 12px;
    break-inside: avoid;
  }
  .sign-item { flex: 1; white-space: nowrap; }
  .sign-line {
    display: inline-block;
    min-width: 110px;
    border-bottom: 1px solid #333;
    padding: 0 4px 1px;
  }
`

function calcUsage(row) {
  if (row.currReading === '' || row.currReading == null || row.prevReading === '' || row.prevReading == null) return ''
  const prev = Number(row.prevReading)
  const curr = Number(row.currReading)
  const ratio = Number(row.ratio || 1)
  if (!Number.isFinite(prev) || !Number.isFinite(curr)) return ''
  const diff = curr - prev
  if (diff < 0) return '0'
  return fmtNum(Math.round(diff * (Number.isFinite(ratio) && ratio > 0 ? ratio : 1) * 100) / 100, 2)
}

function renderSectionTable(rows, unitLabel, showFilled) {
  if (!rows.length) return ''
  const body = rows.map((r, idx) => {
    const currText = showFilled ? fmtNum(r.currReading, 2) : ''
    const usageText = showFilled ? calcUsage(r) : ''
    return `<tr class="${r.meterRole === 'PROPERTY' ? 'prop-row' : ''}">
      <td class="c">${idx + 1}</td>
      <td class="c">${esc(r.area)}</td>
      <td>${esc(r.tenantDisplay)}</td>
      <td class="c">${esc(r.code)}</td>
      <td class="c">${esc(r.ratio ?? 1)}</td>
      <td class="r">${fmtNum(r.prevReading, 2)}</td>
      <td class="r write-box">${currText}</td>
      <td class="r write-box">${usageText}</td>
      <td class="c sign-box"></td>
      <td>${esc(r.remark)}</td>
    </tr>`
  }).join('')

  return `<table class="grid-table">
    <thead>
      <tr>
        <th style="width:42px">序号</th>
        <th style="width:86px">区域/楼层</th>
        <th style="min-width:165px">租户 / 使用单位</th>
        <th style="width:110px">表计编号</th>
        <th style="width:48px">倍率</th>
        <th style="width:88px">上期读数</th>
        <th style="width:94px">本期读数</th>
        <th style="width:92px">实用量(${esc(unitLabel)})</th>
        <th style="width:92px">现场/租户签字</th>
        <th style="min-width:110px">备注</th>
      </tr>
    </thead>
    <tbody>${body}</tbody>
  </table>`
}

/** 生成水电表抄表单完整 HTML 片段 */
export function buildReadingSheetHtml(meta, rows, options = {}) {
  const showFilled = !!options.showFilled
  const elecRows = rows.filter((r) => r.energyType === '电')
  const waterRows = rows.filter((r) => r.energyType === '水')
  const both = elecRows.length > 0 && waterRows.length > 0

  let sectionsHtml = ''
  if (elecRows.length > 0) {
    if (both) {
      sectionsHtml += `<div class="sec-head"><div>一、电表抄表记录</div><span>共 ${elecRows.length} 块表 · 单位：度(kWh)</span></div>`
    }
    sectionsHtml += renderSectionTable(elecRows, '度', showFilled)
  }
  if (waterRows.length > 0) {
    if (both) {
      sectionsHtml += `<div class="sec-head"><div>二、水表抄表记录</div><span>共 ${waterRows.length} 块表 · 单位：m³</span></div>`
    }
    sectionsHtml += renderSectionTable(waterRows, 'm³', showFilled)
  }
  if (!sectionsHtml) {
    sectionsHtml = '<div style="text-align:center;padding:30px 0;color:#888">暂未勾选任何表计行</div>'
  }

  const modeHint = showFilled
    ? '模式：含本期读数核对确认单（供租户及工程部核对签字留档）'
    : '模式：现场抄表记录单（上期读数已锁定预填，现场手填本期读数并签字确认）'

  return `<section class="reading-sheet">
    <h1 class="sheet-title">${esc(meta.title)}</h1>
    <p class="sheet-sub">${esc(modeHint)}</p>
    <table class="meta-table">
      <tr>
        <th>园区项目</th><td>${esc(meta.parkName)}</td>
        <th>计费账期</th><td>${esc(meta.periodText)}</td>
        <th>抄表日期</th><td>${esc(meta.readDate)}</td>
      </tr>
      <tr>
        <th>表计统计</th><td>电表 ${elecRows.length} 块 / 水表 ${waterRows.length} 块（共 ${rows.length} 块）</td>
        <th>抄表人</th><td>${esc(meta.reader)}</td>
        <th>复核人</th><td>${esc(meta.reviewer)}</td>
      </tr>
    </table>
    ${sectionsHtml}
    ${meta.notes ? `<div class="notes-box">${esc(meta.notes)}</div>` : ''}
    <div class="sign-bar">
      <div class="sign-item">抄表人签字：<span class="sign-line">${esc(meta.reader)}</span></div>
      <div class="sign-item">复核人签字：<span class="sign-line">${esc(meta.reviewer)}</span></div>
      <div class="sign-item">工程/物业主管：<span class="sign-line"></span></div>
      <div class="sign-item">日期：<span class="sign-line">${esc(meta.readDate)}</span></div>
    </div>
  </section>`
}

/** 构造独立可打印 HTML 文档 */
export function buildReadingSheetDocument(meta, rows, options = {}) {
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>${esc(meta.title)}</title><style>${METER_READING_PRINT_CSS}</style></head><body>${buildReadingSheetHtml(meta, rows, options)}</body></html>`
}

/** 唤起浏览器 A4 横向打印 */
export function printReadingSheet(meta, rows, options = {}) {
  const iframe = document.createElement('iframe')
  iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0'
  document.body.appendChild(iframe)
  const doc = iframe.contentDocument
  doc.open()
  doc.write(buildReadingSheetDocument(meta, rows, options))
  doc.close()
  const cleanup = () => { try { document.body.removeChild(iframe) } catch {} }
  iframe.contentWindow.onafterprint = cleanup
  setTimeout(() => {
    iframe.contentWindow.focus()
    iframe.contentWindow.print()
    setTimeout(cleanup, 60000)
  }, 120)
}

/** 导出多页自动分页 A4 横向 PDF */
export async function downloadReadingSheetPdf(meta, rows, options = {}, fileName = '水电表抄表单.pdf') {
  const [{ default: html2canvas }, { jsPDF: JsPDF }] = await Promise.all([
    import('html2canvas'),
    import('jspdf')
  ])
  const iframe = document.createElement('iframe')
  iframe.style.cssText = 'position:fixed;left:-10000px;top:0;width:1180px;height:820px;border:0;visibility:hidden'
  document.body.appendChild(iframe)
  try {
    const doc = iframe.contentDocument
    doc.open()
    doc.write(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>${METER_READING_PRINT_CSS}
      html, body { margin: 0; padding: 0; background: #fff; }
      body { padding: 16px 20px; width: 1120px; }
    </style></head><body>${buildReadingSheetHtml(meta, rows, options)}</body></html>`)
    doc.close()
    await new Promise((r) => setTimeout(r, 80))

    const target = doc.querySelector('.reading-sheet') || doc.body
    const canvas = await html2canvas(target, {
      scale: 2,
      useCORS: true,
      backgroundColor: '#ffffff',
      logging: false,
      windowWidth: 1160
    })

    const pdf = new JsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' })
    const pageW = 297
    const pageH = 210
    const marginX = 10
    const marginY = 10
    const contentW = pageW - marginX * 2
    const contentH = pageH - marginY * 2

    const imgW = contentW
    const imgH = (canvas.height * imgW) / canvas.width
    const pageCanvasH = Math.floor((contentH / imgW) * canvas.width)

    if (imgH <= contentH) {
      pdf.addImage(canvas.toDataURL('image/jpeg', 0.95), 'JPEG', marginX, marginY, imgW, imgH, undefined, 'FAST')
    } else {
      let offsetY = 0
      let pageIndex = 0
      while (offsetY < canvas.height) {
        const sliceH = Math.min(pageCanvasH, canvas.height - offsetY)
        const sliceCanvas = document.createElement('canvas')
        sliceCanvas.width = canvas.width
        sliceCanvas.height = sliceH
        const ctx = sliceCanvas.getContext('2d')
        ctx.fillStyle = '#ffffff'
        ctx.fillRect(0, 0, sliceCanvas.width, sliceCanvas.height)
        ctx.drawImage(canvas, 0, offsetY, canvas.width, sliceH, 0, 0, canvas.width, sliceH)
        if (pageIndex > 0) pdf.addPage()
        const drawH = (sliceH * imgW) / canvas.width
        pdf.addImage(sliceCanvas.toDataURL('image/jpeg', 0.95), 'JPEG', marginX, marginY, imgW, drawH, undefined, 'FAST')
        offsetY += sliceH
        pageIndex++
      }
    }
    pdf.save(fileName.endsWith('.pdf') ? fileName : `${fileName}.pdf`)
  } finally {
    try { document.body.removeChild(iframe) } catch {}
  }
}
