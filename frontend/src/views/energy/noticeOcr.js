// 浏览器端中文 OCR（自托管 Tesseract.js WASM + chi_sim/eng，零外部 CDN 依赖）

let workerPromise = null
let activeProgressCb = null

export function isImageFile(file) {
  if (!file) return false
  const name = (file.name || '').toLowerCase()
  const type = (file.type || '').toLowerCase()
  return type.startsWith('image/') || /\.(jpg|jpeg|png|webp|bmp)$/.test(name)
}

async function getWorker() {
  if (!workerPromise) {
    workerPromise = (async () => {
      const { createWorker } = await import('tesseract.js')
      const origin = window.location.origin
      const base = (import.meta.env.BASE_URL || '/').replace(/\/+$/, '')
      const prefix = `${origin}${base}/ocr`
      // 显式指定 .wasm.js 路径，避免 tesseract.js v7 在新版 Chrome/Edge 上尝试拉取 relaxedsimd 报 404
      const worker = await createWorker(['chi_sim', 'eng'], 1, {
        workerPath: `${prefix}/worker.min.js`,
        corePath: `${prefix}/core/tesseract-core-lstm.wasm.js`,
        langPath: `${prefix}/lang`,
        logger: (m) => {
          if (activeProgressCb && m?.status === 'recognizing text' && typeof m.progress === 'number') {
            activeProgressCb(Math.round(m.progress * 100))
          }
        }
      })
      return worker
    })().catch((err) => {
      workerPromise = null
      throw err
    })
  }
  return workerPromise
}

/** 轻量图像增强：小图放大 + 灰度高对比度处理，提升银行回执与发票数字/中文 OCR 准确率 */
function preprocessImage(file) {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      try {
        let w = img.naturalWidth || img.width
        let h = img.naturalHeight || img.height
        let scale = 1
        if (w < 1600) scale = Math.min(2.2, 2000 / Math.max(w, 1))
        else if (w > 2800) scale = 2800 / w
        const cw = Math.max(1, Math.round(w * scale))
        const ch = Math.max(1, Math.round(h * scale))
        const canvas = document.createElement('canvas')
        canvas.width = cw
        canvas.height = ch
        const ctx = canvas.getContext('2d', { willReadFrequently: true })
        ctx.fillStyle = '#ffffff'
        ctx.fillRect(0, 0, cw, ch)
        ctx.drawImage(img, 0, 0, cw, ch)
        const imageData = ctx.getImageData(0, 0, cw, ch)
        const d = imageData.data
        for (let i = 0; i < d.length; i += 4) {
          const gray = 0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2]
          const boosted = Math.max(0, Math.min(255, (gray - 128) * 1.25 + 128))
          d[i] = d[i + 1] = d[i + 2] = boosted
        }
        ctx.putImageData(imageData, 0, 0)
        resolve(canvas)
      } catch {
        resolve(file)
      } finally {
        URL.revokeObjectURL(url)
      }
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(file)
    }
    img.src = url
  })
}

export async function recognizeImageText(file, onProgress) {
  activeProgressCb = typeof onProgress === 'function' ? onProgress : null
  try {
    const [worker, input] = await Promise.all([getWorker(), preprocessImage(file)])
    const { data } = await worker.recognize(input)
    return (data?.text || '').trim()
  } finally {
    activeProgressCb = null
  }
}
