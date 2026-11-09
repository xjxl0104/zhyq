import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/pages/referral/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
function harness(phone) {
  const sent = [], notices = []
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value, onLoad() {},
    bizApi: { referral: async body => { sent.push(body); return {} } },
    uni: { showToast: x => notices.push(x.title), showModal() {} }
  })
  vm.runInContext(source, context)
  context.phone = phone
  vm.runInContext("form.name = '客户'; form.phone = phone", context)
  return { sent, notices, submit: () => vm.runInContext('submit()', context) }
}
test('empty or whitespace phone submits as null', async () => {
  for (const phone of ['', '   ']) {
    const h = harness(phone); await h.submit()
    assert.equal(h.sent.length, 1); assert.equal(h.sent[0].phone, null)
  }
})
test('provided phone is validated and normalized', async () => {
  const invalid = harness('123'); await invalid.submit(); assert.equal(invalid.sent.length, 0)
  const valid = harness(' 13800138000 '); await valid.submit()
  assert.equal(valid.sent[0].phone, '13800138000')
})
