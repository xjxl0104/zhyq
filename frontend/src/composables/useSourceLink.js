import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { workOrderApi } from '@/api/property'

/**
 * 工单来源联动(双向):
 *  - 正向:工单详情 →「查看来源记录」跳到源页面并筛出那一条(靠 query.highlightId)
 *  - 反向:源记录行 →「关联工单」看它派生出的工单
 *
 * 五个页面(巡检/巡更/三检/投诉/告警)共用,所以抽成 composable,
 * 别在各页面里重复实现。
 */

/**
 * 来源类型 → 前端路由,正反向跳转共用一份映射。
 *
 * 每个值必须是 router/index.js 里真实存在的 path — 项目没有 404 兜底路由,
 * 写错会跳到空白页(这里踩过:CHECK 曾写成不存在的 /property/check)。
 * 有测试校验本映射与路由表一致,改动后记得跑 pnpm test。
 *
 * CHECK 特例:三检按 ctype 拆成 check-clean/green/quality 三条路由,而工单只存了
 * sourceId 不知类型,无法定向。统一落到 check-clean,由 Check.vue 在定位态下
 * 丢掉 ctype 过滤来保证跨类型也能查到(见该文件 load() 注释)。
 */
export const SOURCE_ROUTES = {
  INSPECTION_PLAN: '/property/inspection',
  PATROL: '/property/patrol',
  CHECK: '/property/check-clean',
  FEEDBACK: '/property/feedback',
  ALARM: '/iot/alarm'
}

/** 来源类型 → 中文名,用于提示语与弹窗标题 */
export const SOURCE_LABELS = {
  INSPECTION_PLAN: '巡检计划',
  PATROL: '安防巡更',
  CHECK: '三检记录',
  FEEDBACK: '投诉反馈',
  ALARM: '告警'
}

/**
 * 源页面侧:消费 route.query.highlightId,把列表筛成那一条。
 *
 * @param {object} query 页面的查询条件 reactive 对象(须含 pageNo)
 * @param {Function} load 页面既有的列表加载函数
 * @param {{ immediate?: boolean }} [options] immediate=false 时不自挂 onMounted,
 *   由页面在自己的 onMounted 里调 applyHighlight() 决定时机。
 *   页面已有 onMounted 且会自行取数时必须用 false, 否则会请求两次、
 *   且页面那次的取数会把定位条件冲掉。
 */
export function useHighlightFilter(query, load, options = {}) {
  const { immediate = true } = options
  const route = useRoute()
  const router = useRouter()
  const highlightId = ref(null)

  const isHighlighting = computed(() => highlightId.value != null)

  /**
   * 清除定位,恢复完整列表。
   * 只摘掉 highlightId 一个键,别整个清空 query — 工单页还靠 route.query.status
   * 从汇总页接筛选条件,一把清了会连带丢掉。
   *
   * 边界:本函数只负责 highlightId / query.id 这一条链路,其余 query.* 字段
   * 由页面自己的表单绑定管理。以后若在 applyHighlight 里多落了字段到 query 上,
   * 记得在这里同步清掉,否则「显示全部」会清不干净。
   */
  function clearHighlight() {
    highlightId.value = null
    query.id = null
    query.pageNo = 1
    const rest = { ...route.query }
    delete rest.highlightId
    router.replace({ path: route.path, query: rest })
    load()
  }

  /** 给定位到的那行加底色。列表被筛成一条时其实只有一行,留着是为了后续改成翻页高亮也能用 */
  function rowClass({ row }) {
    return row.id === highlightId.value ? 'source-highlight-row' : ''
  }

  /**
   * 查询栏的「查询」按钮走这个,替代直接调 load。
   *
   * 用户主动改筛选条件再查询, 意味着他已经不关心那条定位记录了。
   * 若保留 query.id, 会得到"既按 id 锁定又叠加新筛选"的中间态,
   * 而提示条还写着"仅显示该条记录"—— 那时它已经不成立了。
   */
  function search() {
    if (highlightId.value != null) clearHighlight()
    else load()
  }

  /**
   * 读 url 上的 highlightId 并落到 query.id 上。
   * @returns {boolean} 是否命中定位(页面据此决定还要不要再取一次数)
   */
  function applyHighlight() {
    const raw = Number(route.query.highlightId)
    // Number('') === 0,得排掉 0 和 NaN,否则空值会被当成 id=0 查出空列表
    if (!Number.isFinite(raw) || raw <= 0) return false
    highlightId.value = raw
    query.id = raw
    query.pageNo = 1
    return true
  }

  if (immediate) {
    onMounted(() => {
      if (applyHighlight()) load()
    })
  } else if (import.meta.env.DEV) {
    // immediate:false 要求页面自己调 applyHighlight()。忘了调不会报错,
    // 只是定位静默失效 —— 开发期主动喊一声,别让它悄悄坏掉。
    onMounted(() => {
      setTimeout(() => {
        if (route.query.highlightId && highlightId.value == null) {
          console.warn(
            '[useSourceLink] url 带了 highlightId 但定位未生效,' +
            '页面用了 immediate:false 却没在 onMounted 里调 applyHighlight()'
          )
        }
      }, 0)
    })
  }

  return { highlightId, isHighlighting, clearHighlight, rowClass, applyHighlight, search }
}

/**
 * 源页面侧:反查每行派生的工单。
 *
 * @param {string} sourceType 本页对应的来源类型,取 SOURCE_ROUTES 的键
 * @returns 徽标计数 map、抽屉状态与打开/加载函数
 */
export function useRelatedOrders(sourceType) {
  const router = useRouter()
  /** sourceId → 工单数,列表徽标用 */
  const counts = ref({})
  const drawer = ref({ visible: false, sourceId: null, loading: false, list: [] })

  /**
   * 列表加载完调一次,批量取本页各行的工单数。
   *
   * 故意不返回 Promise 给调用方 await:徽标是辅助信息,不该参与主列表的
   * loading 计时。await 的话统计接口一慢,表格数据早到了却还蒙着遮罩空转。
   */
  function loadCounts(rows) {
    const ids = (rows || []).map((r) => r.id).filter(Boolean)
    if (!ids.length) {
      counts.value = {}
      return
    }
    workOrderApi
      .countBySource(sourceType, ids)
      .then((res) => {
        counts.value = res || {}
      })
      .catch(() => {
        // 取不到就不显示徽标,不该阻断列表本身
        counts.value = {}
      })
  }

  function countOf(id) {
    return counts.value?.[id] || 0
  }

  async function openRelated(row) {
    drawer.value = { visible: true, sourceId: row.id, loading: true, list: [] }
    try {
      drawer.value.list = await workOrderApi.bySource(sourceType, row.id)
    } finally {
      drawer.value.loading = false
    }
  }

  /** 从抽屉点进工单页,反向也筛成那一条 */
  function gotoOrder(order) {
    drawer.value.visible = false
    router.push({ path: '/property/workorder', query: { highlightId: order.id } })
  }

  return { counts, countOf, loadCounts, drawer, openRelated, gotoOrder }
}
