export function refreshHistory() {
  const lastSuccess = new Map()
  return function record(results) {
    const time = new Date().toLocaleString('zh-CN', { hour12: false })
    const missing = []
    for (const [label, success] of results) {
      if (success) lastSuccess.set(label, time)
      else missing.push(label + '（' + (lastSuccess.get(label) || '尚无成功记录') + '）')
    }
    if (!missing.length) return { updated: time, warning: '' }
    const message = missing.length === results.length ? '刷新失败' : '部分数据未更新'
    return { updated: '', warning: message + ' · ' + missing.join('、') }
  }
}
