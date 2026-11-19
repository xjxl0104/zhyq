import request from '@/utils/request'

// 资料管理 · 资料库
export const BIZ_TYPE = 'archive_doc'
const LONG_TIMEOUT = 10 * 60 * 1000

export const archiveApi = {
  meta: () => request.get('/archive/doc/meta'),
  stats: () => request.get('/archive/doc/stats'),
  page: (params) => request.get('/archive/doc/page', { params }),
  get: (id) => request.get(`/archive/doc/${id}`),
  add: (data) => request.post('/archive/doc', data),
  update: (data) => request.put('/archive/doc', data),
  move: (data) => request.put('/archive/doc/move', data),
  remove: (id) => request.delete(`/archive/doc/${id}`),
  // 先把文件传到附件体系(bizType=archive_doc),再让服务端读文件分析
  upload(file) {
    const form = new FormData()
    form.append('file', file)
    form.append('bizType', BIZ_TYPE)
    return request.post('/file/upload', form, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: LONG_TIMEOUT })
  },
  analyze: (data) => request.post('/archive/doc/analyze', data, { timeout: 2 * 60 * 1000 })
}

// 资料管理 · 文件夹(像电脑文件夹一样,无限层级)
export const folderApi = {
  tree: (projectId) => request.get('/archive/folder/tree', { params: { projectId } }),
  create: (data) => request.post('/archive/folder', data),
  rename: (data) => request.put('/archive/folder', data),
  remove: (id) => request.delete(`/archive/folder/${id}`),
  breadcrumb: (id) => request.get('/archive/folder/breadcrumb', { params: { id } })
}

// 资料管理 · 分类与规则(高级,可选用)
export const archiveSettingApi = {
  categories: () => request.get('/archive/setting/category/list'),
  addCategory: (data) => request.post('/archive/setting/category', data),
  updateCategory: (data) => request.put('/archive/setting/category', data),
  removeCategory: (id) => request.delete(`/archive/setting/category/${id}`),
  rulePage: (params) => request.get('/archive/setting/rule/page', { params }),
  addRule: (data) => request.post('/archive/setting/rule', data),
  updateRule: (data) => request.put('/archive/setting/rule', data),
  removeRule: (id) => request.delete(`/archive/setting/rule/${id}`),
  test: (data) => request.post('/archive/setting/test', data)
}

export const SECRET_OPTIONS = [
  { value: 1, label: '公开', type: 'success' },
  { value: 2, label: '内部', type: 'info' },
  { value: 3, label: '机密', type: 'danger' }
]
export const STATUS_OPTIONS = [
  { value: 1, label: '现行', type: 'success' },
  { value: 2, label: '失效', type: 'info' }
]

export function secretOf(v) {
  return SECRET_OPTIONS.find((o) => o.value === v) || { label: '-', type: 'info' }
}
export function statusOf(v) {
  return STATUS_OPTIONS.find((o) => o.value === v) || { label: '-', type: 'info' }
}
export function fmtSize(n) {
  if (!n && n !== 0) return '-'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}
/** 把分类树打平成 { 子类编码: '门类名 › 子类名' } */
export function categoryLabelMap(tree) {
  const map = {}
  for (const top of tree || []) {
    map[top.code] = top.name
    for (const sub of top.children || []) map[sub.code] = `${top.name} › ${sub.name}`
  }
  return map
}
