import { downloadBlob } from './feePeriod'

function sanitizeName(name) {
  return String(name || '通知单')
    .replace(/[\\/:*?"<>|]/g, '_')
    .replace(/\s+/g, ' ')
    .trim()
}

function waitImages(doc) {
  const imgs = Array.from(doc.querySelectorAll('img'))
  if (!imgs.length) return Promise.resolve()
  return Promise.all(
    imgs.map((img) => {
      if (img.complete) return Promise.resolve()
      return new Promise((resolve) => {
        img.onload = resolve
        img.onerror = resolve
        setTimeout(resolve, 1500)
      })
    })
  )
}

async function renderHtmlToPdfBuffer(iframe, css, html, html2canvas, JsPDF) {
  const doc = iframe.contentDocument
  doc.open()
  doc.write(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>${css}
    html, body { margin: 0; padding: 0; background: #fff; }
    body { padding: 14px 18px; width: 1080px; box-sizing: border-box; }
    .notice { page-break-after: auto !important; break-after: auto !important; }
  </style></head><body>${html}</body></html>`)
  doc.close()

  await waitImages(doc)
  await new Promise((r) => setTimeout(r, 40))

  const target = doc.querySelector('.notice') || doc.body
  const canvas = await html2canvas(target, {
    scale: 2,
    useCORS: true,
    backgroundColor: '#ffffff',
    logging: false,
    windowWidth: 1120
  })

  const pdf = new JsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' })
  const pageW = 297
  const pageH = 210
  const marginX = 10
  const marginY = 8
  const maxW = pageW - marginX * 2
  const maxH = pageH - marginY * 2

  const ratio = canvas.width / canvas.height
  let drawW = maxW
  let drawH = drawW / ratio
  if (drawH > maxH) {
    drawH = maxH
    drawW = drawH * ratio
  }
  const x = (pageW - drawW) / 2
  const y = marginY

  const imgData = canvas.toDataURL('image/jpeg', 0.94)
  pdf.addImage(imgData, 'JPEG', x, y, drawW, drawH, undefined, 'FAST')
  return pdf.output('arraybuffer')
}

/**
 * 将多张通知单分别生成独立的 PDF 文件，打包为一个 ZIP 下载（绝不合并成单个 PDF）。
 * @param {Object} opts
 * @param {Array<{ fileName: string, html: string }>} opts.items
 * @param {string} opts.css
 * @param {string} opts.zipName
 * @param {(done: number, total: number) => void} [opts.onProgress]
 */
export async function downloadNoticesAsSeparatePdfsZip({ items, css, zipName, onProgress }) {
  if (!items?.length) return
  const [{ default: html2canvas }, { jsPDF }, { default: JSZip }] = await Promise.all([
    import('html2canvas'),
    import('jspdf'),
    import('jszip')
  ])

  const zip = new JSZip()
  const iframe = document.createElement('iframe')
  iframe.style.cssText = 'position:fixed;left:-99999px;top:0;width:1120px;height:900px;border:0;opacity:0;pointer-events:none;background:#fff;'
  document.body.appendChild(iframe)

  try {
    const usedNames = new Map()
    for (let i = 0; i < items.length; i++) {
      const it = items[i]
      const buf = await renderHtmlToPdfBuffer(iframe, css, it.html, html2canvas, jsPDF)
      let base = sanitizeName(it.fileName).replace(/\.pdf$/i, '')
      const count = usedNames.get(base) || 0
      usedNames.set(base, count + 1)
      const finalName = count === 0 ? `${base}.pdf` : `${base}_${count + 1}.pdf`
      zip.file(finalName, buf)
      if (onProgress) onProgress(i + 1, items.length)
    }
    const blob = await zip.generateAsync({ type: 'blob' })
    const cleanZip = sanitizeName(zipName).replace(/\.zip$/i, '') + '.zip'
    downloadBlob(blob, cleanZip)
  } finally {
    iframe.remove()
  }
}
