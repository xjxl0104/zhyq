import test from 'node:test'
import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'src')
const walk = dir => readdirSync(dir).flatMap(name => {
  const full = join(dir, name)
  return statSync(full).isDirectory() ? walk(full) : full.endsWith('.vue') ? [full] : []
})

// 微信小程序运行时没有 _ctx.uni：模板里直接写 uni.navigateTo(...) 在 H5 能用，在小程序里点击会报错。
test('模板中不能直接引用全局 uni，跳转走脚本函数', () => {
  const offenders = []
  for (const file of walk(root)) {
    const source = readFileSync(file, 'utf8')
    const template = source.match(/<template>[\s\S]*<\/template>/)?.[0] || ''
    if (/\buni\./.test(template)) offenders.push(file.slice(root.length + 1))
  }
  assert.deepEqual(offenders, [])
})
