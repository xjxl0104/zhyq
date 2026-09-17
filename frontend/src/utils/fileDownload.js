import { fileApi } from '@/api/file'

// 网页只请求短时凭证，文件流直接交由浏览器下载管理器处理，不在页面内缓存 Blob。
export async function startFileDownload(id, filename) {
  const { ticket } = await fileApi.createDownloadTicket(id)
  if (!ticket) throw new Error('未取得下载凭证，请重试')
  const link = document.createElement('a')
  link.href = `/api/file/browser-download/${encodeURIComponent(id)}?ticket=${encodeURIComponent(ticket)}`
  link.download = filename || '附件'
  link.rel = 'noreferrer'
  link.style.display = 'none'
  document.body.appendChild(link)
  try {
    link.click()
  } finally {
    link.remove()
  }
}
