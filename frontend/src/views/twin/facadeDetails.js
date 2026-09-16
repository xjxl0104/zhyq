import { Color } from 'three'

const installed = new WeakMap()
const number = value => Number(value).toFixed(6)

/** Reinstall screen-filtered facade detail from the plain material extras in a GLB. */
export function installFacadeDetails(material) {
  const detail = material.userData?.facadeDetail
  if (!detail || !['glass-grid', 'corrugation'].includes(detail.kind)) return
  const key = 'facade-detail-v1:' + JSON.stringify(detail)
  const existing = installed.get(material)
  if (existing?.key === key) return
  const previous = existing?.previous || material.onBeforeCompile
  installed.set(material, { key, previous })
  material.onBeforeCompile = (shader, renderer) => {
    previous?.call(material, shader, renderer)
    shader.vertexShader = 'varying vec2 vFacadeSurface;\n' + shader.vertexShader
    shader.vertexShader = shader.vertexShader.replace('#include <begin_vertex>', `
      #include <begin_vertex>
      // Geometry is already baked into floor-local coordinates by the batcher.
      vFacadeSurface = vec2(abs(normal.x) > 0.5 ? position.z : position.x, position.y);
    `)
    shader.fragmentShader = `
      varying vec2 vFacadeSurface;
      // Integrate a thin line over the pixel footprint instead of point-sampling
      // it. Fade details before their period falls below two screen pixels.
      float facadeLineCoverage(float coordinate, float pitch, float width) {
        float pixelWidth = max(fwidth(coordinate), 0.00001);
        float distanceToLine = abs(fract(coordinate / pitch + 0.5) - 0.5) * pitch;
        float overlap = max(0.0, min(width * 0.5, distanceToLine + pixelWidth * 0.5)
          - max(-width * 0.5, distanceToLine - pixelWidth * 0.5));
        float visibility = 1.0 - smoothstep(pitch * 0.18, pitch * 0.5, pixelWidth);
        return clamp(overlap / pixelWidth, 0.0, 1.0) * visibility;
      }
    ` + shader.fragmentShader
    let shading
    if (detail.kind === 'glass-grid') {
      const color = new Color(detail.color)
      const horizontal = detail.pitch[1] > 0
        ? `facadeLineCoverage(vFacadeSurface.y, ${number(detail.pitch[1])}, ${number(detail.width[1])})`
        : '0.0'
      shading = `
        float verticalFrame = facadeLineCoverage(vFacadeSurface.x, ${number(detail.pitch[0])}, ${number(detail.width[0])});
        float horizontalFrame = ${horizontal};
        float frameCoverage = 1.0 - (1.0 - verticalFrame) * (1.0 - horizontalFrame);
        // Filter in premultiplied space: a partly covered opaque mullion must
        // retain its contrast against the transparent pane. Return straight alpha
        // for Three's normal material blending after integrating the coverage.
        float paneAlpha = diffuseColor.a;
        float combinedAlpha = mix(paneAlpha, 0.9, frameCoverage);
        diffuseColor.rgb = mix(diffuseColor.rgb * paneAlpha, vec3(${color.toArray().map(number).join(',')}) * 0.9, frameCoverage) / max(combinedAlpha, 0.00001);
        diffuseColor.a = combinedAlpha;
      `
    } else {
      const axis = detail.axis === 'y' ? 'y' : 'x'
      shading = `
        float ribPhase = vFacadeSurface.${axis} / ${number(detail.pitch)};
        float ribVisibility = 1.0 - smoothstep(0.15, 0.45, fwidth(ribPhase));
        // Preserve average brightness when the grooves become subpixel.
        float ribRelief = 0.5 + 0.5 * cos(ribPhase * 6.28318530718) * ribVisibility;
        diffuseColor.rgb *= 1.0 - ${number(detail.contrast)} * ribRelief;
      `
    }
    shader.fragmentShader = shader.fragmentShader.replace('#include <color_fragment>', '#include <color_fragment>\n' + shading)
  }
  material.customProgramCacheKey = () => key
  material.needsUpdate = true
}
