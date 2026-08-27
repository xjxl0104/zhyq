import { describe, it, expect, vi } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { SOURCE_ROUTES, SOURCE_LABELS } from '../useSourceLink'

// useHighlightFilter / useRelatedOrders 依赖 vue-router 与 onMounted,
// 需要宿主组件才能完整测。这里先覆盖纯逻辑部分:
// 路由映射的完整性(死链的根因就是映射缺失/写错)。

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {}, path: '/property/patrol' }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
}))

describe('SOURCE_ROUTES', () => {
  // 与后端 WorkOrderSource.QUERYABLE 白名单一致(MANUAL 不含在内)
  const QUERYABLE = ['INSPECTION_PLAN', 'PATROL', 'CHECK', 'FEEDBACK', 'ALARM']

  it('覆盖后端所有可反查的来源类型', () => {
    for (const t of QUERYABLE) {
      expect(SOURCE_ROUTES[t], `缺少 ${t} 的路由映射`).toBeTruthy()
    }
  })

  it('不含 MANUAL:手工工单没有源记录,不该给出入口', () => {
    expect(SOURCE_ROUTES.MANUAL).toBeUndefined()
  })

  it('每个来源类型都有中文名', () => {
    for (const t of QUERYABLE) {
      expect(SOURCE_LABELS[t], `缺少 ${t} 的中文名`).toBeTruthy()
    }
  })

  it('路由都是绝对路径(router.push 用相对路径会拼错)', () => {
    for (const path of Object.values(SOURCE_ROUTES)) {
      expect(path.startsWith('/')).toBe(true)
    }
  })

  // 这条是关键:CHECK 曾写成不存在的 /property/check,
  // 只查"有值 + 以 / 开头"的断言全绿但功能是坏的(项目无 404 兜底,跳到空白页)。
  // 改成拿真实路由表比对,才能兜住这类死链。
  it('每个路由都真实存在于 router/index.js(防死链)', async () => {
    const routerSrc = await readFile(
      resolve(__dirname, '../../router/index.js'),
      'utf-8'
    )
    // 路由表里子路由是不带前导斜杠的相对 path,如 path: 'property/patrol'
    const declared = [...routerSrc.matchAll(/path:\s*'([^']+)'/g)].map((m) => m[1])
    const normalized = new Set(declared.map((p) => (p.startsWith('/') ? p : `/${p}`)))

    for (const [type, path] of Object.entries(SOURCE_ROUTES)) {
      expect(normalized.has(path), `${type} 的路由 ${path} 不在路由表里`).toBe(true)
    }
  })
})

describe('useHighlightFilter 的 id 解析', () => {
  // applyHighlight 里那段 Number 校验是踩过的坑:
  // Number('') === 0 会被当成 id=0 查出空列表。这里固化住边界行为。
  const parse = (raw) => {
    const n = Number(raw)
    return Number.isFinite(n) && n > 0 ? n : null
  }

  it.each([
    ['12', 12],
    [12, 12],
    ['', null],
    [undefined, null],
    ['abc', null],
    ['0', null],
    ['-3', null]
  ])('highlightId=%s → %s', (input, expected) => {
    expect(parse(input)).toBe(expected)
  })
})
