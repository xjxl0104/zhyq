import * as THREE from 'three'

// DIPARK: the teal DI consists of an open D and a solid I, followed by PARK.
// Units are the PARK cap height. Do not add the previously misread C-shaped
// stroke between the I and P.
export const REFERENCE_WORDMARK_WIDTH = 4.833
function openBowl() {
  const shape = new THREE.Shape()
  shape.moveTo(0, .96)
  shape.lineTo(.46, .96)
  shape.bezierCurveTo(.8, .96, .99, .8, .99, .5)
  shape.bezierCurveTo(.99, .2, .8, .04, .46, .04)
  shape.lineTo(0, .04)
  shape.lineTo(0, .34)
  shape.lineTo(.44, .34)
  shape.bezierCurveTo(.57, .34, .63, .4, .63, .5)
  shape.bezierCurveTo(.63, .6, .57, .66, .44, .66)
  shape.lineTo(0, .66)
  shape.closePath()
  return shape
}

function polygon(points) {
  const shape = new THREE.Shape()
  points.forEach(([x, y], index) => index ? shape.lineTo(x, y) : shape.moveTo(x, y))
  shape.closePath()
  return shape
}

// The narrow, light PARK is drawn as outlines with actual open counters. These
// are geometric sign letters, without a typeface dependency or raster decal.
function letterP() {
  const shape = new THREE.Shape()
  shape.moveTo(0, 0)
  shape.lineTo(0, 1)
  shape.lineTo(.35, 1)
  shape.bezierCurveTo(.56, 1, .66, .9, .66, .735)
  shape.bezierCurveTo(.66, .57, .56, .47, .35, .47)
  shape.lineTo(.085, .47)
  shape.lineTo(.085, 0)
  shape.closePath()
  const counter = new THREE.Path()
  counter.moveTo(.085, .555)
  counter.lineTo(.35, .555)
  counter.bezierCurveTo(.505, .555, .572, .62, .572, .735)
  counter.bezierCurveTo(.572, .85, .505, .915, .35, .915)
  counter.lineTo(.085, .915)
  counter.closePath()
  shape.holes.push(counter)
  return shape
}

function letterA() {
  const shape = polygon([
    [0, 0], [.322, 1], [.418, 1], [.74, 0], [.645, 0],
    [.551, .295], [.189, .295], [.095, 0],
  ])
  const counter = new THREE.Path()
  counter.moveTo(.217, .38)
  counter.lineTo(.523, .38)
  counter.lineTo(.37, .866)
  counter.closePath()
  shape.holes.push(counter)
  return shape
}

function letterR() {
  const shape = new THREE.Shape()
  shape.moveTo(0, 0)
  shape.lineTo(0, 1)
  shape.lineTo(.35, 1)
  shape.bezierCurveTo(.56, 1, .66, .9, .66, .735)
  shape.bezierCurveTo(.66, .59, .58, .5, .435, .478)
  shape.lineTo(.66, 0)
  shape.lineTo(.559, 0)
  shape.lineTo(.337, .47)
  shape.lineTo(.085, .47)
  shape.lineTo(.085, 0)
  shape.closePath()
  shape.holes.push(letterP().holes[0])
  return shape
}

function letterK() {
  return polygon([
    [0, 0], [0, 1], [.085, 1], [.085, .487], [.566, 1],
    [.683, 1], [.323, .632], [.68, 0], [.578, 0],
    [.257, .563], [.085, .39], [.085, 0],
  ])
}

/**
 * Add the six-letter DIPARK sign directly to a facade shell. Shared
 * materials belong to the caller; each unique geometry is owned by the shell
 * batcher / warehouse disposer. x/y/z are the baseline-left origin, rotation is
 * around Y, and height is the PARK cap height in warehouse model units.
 */
export function addReferenceWordmark(group, materials, {
  x, y, z, height, rotation = 0, variant = 'box',
}) {
  const letteringMaterial = materials[variant === 'roof' ? 'facadeLogoBlue' : 'facadeLogoWhite']
  const glyphs = [
    { part: 'open-d', shape: openBowl(), offset: 0, monogram: true },
    { part: 'stem', shape: polygon([[0, .04], [.36, .04], [.36, .96], [0, .96]]), offset: 1.15, monogram: true },
    { part: 'P', shape: letterP(), offset: 1.67 },
    { part: 'A', shape: letterA(), offset: 2.47 },
    { part: 'R', shape: letterR(), offset: 3.35 },
    { part: 'K', shape: letterK(), offset: 4.15 },
  ]
  const rotationMatrix = new THREE.Matrix4().makeRotationY(rotation)
  for (const glyph of glyphs) {
    const geometry = new THREE.ExtrudeGeometry(glyph.shape, {
      depth: .035, bevelEnabled: false, curveSegments: 10, steps: 1,
    })
    geometry.translate(glyph.offset, 0, 0).scale(height, height, height)
    // The warehouse's material batcher merges indexed architecture. Retaining
    // separate extruded front/side vertices also retains the sharp sign edges.
    geometry.setIndex(Array.from({ length: geometry.attributes.position.count }, (_, index) => index))
    geometry.applyMatrix4(rotationMatrix)
    const mesh = new THREE.Mesh(geometry, glyph.monogram ? materials.facadeLogoTeal : letteringMaterial)
    mesh.name = `reference-wordmark-${variant}-${glyph.part}`
    mesh.position.set(x, y, z)
    mesh.userData.referenceWordmark = variant
    mesh.userData.wordmarkPart = glyph.part
    mesh.castShadow = true
    mesh.receiveShadow = true
    group.add(mesh)
  }
  return { width: REFERENCE_WORDMARK_WIDTH * height, height }
}
