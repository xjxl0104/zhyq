// Keeps page advancement tied to a successful response; a newer refresh wins over old requests.
export function createPager(fetchPage, state, { pageSize = 20 } = {}) {
  Object.assign(state, { records: [], total: 0, pageNo: 0, loading: false, loaded: false, error: '' })
  let requestNo = 0
  let currentFilters = {}
  let retryReset = true
  async function load(reset = true, filters = currentFilters) {
    if (!reset && (state.loading || (state.loaded && state.records.length >= state.total))) return
    currentFilters = { ...filters }
    retryReset = reset
    const request = ++requestNo
    const nextPage = reset ? 1 : state.pageNo + 1
    state.loading = true
    state.error = ''
    if (reset) { state.records = []; state.total = 0; state.pageNo = 0; state.loaded = false }
    try {
      const result = await fetchPage({ ...currentFilters, pageNo: nextPage, pageSize })
      if (request !== requestNo) return
      const rows = result.records || []
      state.records = reset ? rows : [...new Map([...state.records, ...rows].map(row => [row.id, row])).values()]
      state.total = Number(result.total || 0)
      state.pageNo = nextPage
      state.loaded = true
    } catch (error) {
      if (request === requestNo) state.error = error.message || '读取失败，请重试'
    } finally {
      if (request === requestNo) state.loading = false
    }
  }
  return { load, retry: () => load(retryReset), invalidate: () => { requestNo++ } }
}
