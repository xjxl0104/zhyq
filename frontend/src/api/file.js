import request from '@/utils/request'

// el-upload 的 action 直传地址(request baseURL 是 /api)
export const uploadUrl = '/api/file/upload'

export const fileApi = {
  upload(formData) {
    return request.post('/file/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
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
  remove(id) {
    return request.delete(`/file/${id}`)
  },
  // 鉴权下载(替代匿名静态 /uploads):返回原始 axios 响应,res.data 为 Blob
  download(id) {
    return request.get(`/file/download/${id}`, { responseType: 'blob' })
  }
}
