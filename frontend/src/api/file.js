import request from '@/utils/request'

// el-upload 的 action 直传地址(request baseURL 是 /api)
export const uploadUrl = '/api/file/upload'

export const fileApi = {
  upload(formData, { timeout } = {}) {
    return request.post('/file/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      ...(timeout === undefined ? {} : { timeout })
    })
  },
  uploadBatch(formData) {
    return request.post('/file/upload-batch', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  list(bizType, bizId) {
    return request.get('/file/list', { params: { bizType, bizId } })
  },
  // 先传后回填:业务保存拿到新 id 后,把已上传附件关联过去
  attach(bizType, bizId, fileIds) {
    return request.post('/file/attach', { bizType, bizId, fileIds })
  },
  async attachVerified(bizType, bizId, fileIds) {
    const count = await this.attach(bizType, bizId, fileIds)
    if (count === fileIds.length) return
    // A previous attempt may have committed before its response was lost.
    const linked = new Set((await this.list(bizType, bizId)).map(file => String(file.id)))
    if (fileIds.some(id => !linked.has(String(id)))) throw new Error('部分附件尚未关联')
  },
  remove(id) {
    return request.delete(`/file/${id}`)
  },
  createDownloadTicket(id) {
    return request.post(`/file/download-ticket/${id}`, {})
  },
  // 鉴权下载(替代匿名静态 /uploads):返回原始 axios 响应,res.data 为 Blob
  download(id) {
    // 合同扫描件可达 100MB，传输时间会超过普通接口的 15 秒超时。
    return request.get(`/file/download/${id}`, { responseType: 'blob', timeout: 10 * 60 * 1000 })
  }
}
