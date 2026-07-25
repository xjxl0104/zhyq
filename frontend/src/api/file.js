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
  remove(id) {
    return request.delete(`/file/${id}`)
  }
}
