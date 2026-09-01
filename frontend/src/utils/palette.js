// 配色工具:hex 转 [0..1] RGB 与调色板线性插值(GrainientBg 轮询用)
export const hexToRgb = hex => {
  const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  if (!m) return [1, 1, 1]
  return [parseInt(m[1], 16) / 255, parseInt(m[2], 16) / 255, parseInt(m[3], 16) / 255]
}

export const lerpPalette = (a, b, t) =>
  a.map((hexA, i) => {
    const ra = hexToRgb(hexA)
    const rb = hexToRgb(b[i])
    return ra.map((v, c) => v + (rb[c] - v) * t)
  })
