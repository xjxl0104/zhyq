import assert from 'node:assert/strict'

// Run against the public gateway, since its Via header affects nginx gzip_static.
const base = process.argv[2]
if (!base) throw new Error('Usage: node scripts/check-model-compression.mjs https://your-site')
const url = new URL('/models/dipark-warehouse.glb', base)
async function check(encoding) {
  const response = await fetch(url, {
    method: 'HEAD',
    headers: { 'Accept-Encoding': encoding, Via: '1.1 model-compression-check' },
    signal: AbortSignal.timeout(15000),
  })
  assert.equal(response.status, 200, 'model must be reachable through the gateway')
  return response.headers
}
const compressed = await check('gzip')
assert.equal(compressed.get('content-encoding'), 'gzip', 'proxied requests must receive the gzip model')
const original = await check('identity')
assert.equal(original.get('content-encoding'), null, 'clients without gzip must receive the original model')
const small = Number(compressed.get('content-length')), large = Number(original.get('content-length'))
assert.ok(small > 0 && small < large, 'compressed model must be smaller than the original')
console.log(`PASS: proxied model ${large} → ${small} bytes (${(100 * (1 - small / large)).toFixed(1)}% smaller)`)
