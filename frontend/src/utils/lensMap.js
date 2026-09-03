// 液态玻璃位移贴图(React Bits GlassSurface 配方,LiquidGlass.vue 同源):
// 红/蓝线性渐变按 difference 混合形成边缘矢量场,内部一块模糊的中灰矩形是"不折射的镜片主体"。
// 相比 LiquidGlass.vue 多两点:四角圆角可各不相同(选中舌头只有左侧两角是圆的),
// 以及可以指定某条边不做折射(舌头贴内容纸的右边、纸的接缝处不能冒出折射带)。
// 贴图必须按元素实际尺寸生成:feImage 会被拉伸到滤镜区域,尺寸不符边缘带就会变形。

export function roundedRectPath(x, y, w, h, radii) {
  const m = Math.min(w, h) / 2
  const [tl, tr, br, bl] = radii.map((r) => Math.max(0, Math.min(r, m)))
  return (
    `M${x + tl} ${y}` +
    `H${x + w - tr}` + (tr ? `A${tr} ${tr} 0 0 1 ${x + w} ${y + tr}` : '') +
    `V${y + h - br}` + (br ? `A${br} ${br} 0 0 1 ${x + w - br} ${y + h}` : '') +
    `H${x + bl}` + (bl ? `A${bl} ${bl} 0 0 1 ${x} ${y + h - bl}` : '') +
    `V${y + tl}` + (tl ? `A${tl} ${tl} 0 0 1 ${x + tl} ${y}` : '') +
    'Z'
  )
}

/**
 * @param {object} o
 * @param {number} o.width  元素宽(px)
 * @param {number} o.height 元素高(px)
 * @param {number[]} o.radii 四角圆角 [左上, 右上, 右下, 左下]
 * @param {number} o.edge 折射带宽度(px)
 * @param {object} [o.flat] 哪些边不折射,如 { right: true }
 * @param {number} [o.brightness] 镜片主体明度 0-100
 * @param {number} [o.opacity] 镜片主体不透明度
 * @param {number} [o.blur] 镜片主体边缘模糊(px),决定折射带的软硬
 */
export function lensMapDataUri({ width, height, radii, edge, flat = {}, brightness = 50, opacity = 0.93, blur = 11 }) {
  const w = Math.max(1, Math.round(width))
  const h = Math.max(1, Math.round(height))
  const l = flat.left ? 0 : edge
  const t = flat.top ? 0 : edge
  const r = flat.right ? 0 : edge
  const b = flat.bottom ? 0 : edge
  const inner = roundedRectPath(l, t, w - l - r, h - t - b, radii.map((x) => Math.max(0, x - edge)))
  const outer = roundedRectPath(0, 0, w, h, radii)
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}">` +
    '<defs>' +
    '<linearGradient id="r" x1="100%" y1="0%" x2="0%" y2="0%"><stop offset="0%" stop-color="#0000"/><stop offset="100%" stop-color="red"/></linearGradient>' +
    '<linearGradient id="b" x1="0%" y1="0%" x2="0%" y2="100%"><stop offset="0%" stop-color="#0000"/><stop offset="100%" stop-color="blue"/></linearGradient>' +
    '</defs>' +
    `<rect width="${w}" height="${h}" fill="black"/>` +
    `<path d="${outer}" fill="url(#r)"/>` +
    `<path d="${outer}" fill="url(#b)" style="mix-blend-mode:difference"/>` +
    `<path d="${inner}" fill="hsl(0 0% ${brightness}% / ${opacity})" style="filter:blur(${blur}px)"/>` +
    '</svg>'
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}
