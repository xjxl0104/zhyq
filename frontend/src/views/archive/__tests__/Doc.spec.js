import { flushPromises, mount } from '@vue/test-utils'
import { reactive, nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ElTree } from 'element-plus'
import Doc from '../Doc.vue'
import { archiveApi, folderApi } from '@/api/archive'

let project
vi.mock('@/stores/project', () => ({ useProjectStore: () => project }))
vi.mock('@/utils/fileDownload', () => ({ startFileDownload: vi.fn() }))
vi.mock('@/api/archive', () => ({
  fmtSize: value => String(value),
  archiveApi: { page: vi.fn(), upload: vi.fn(), add: vi.fn(), update: vi.fn(), move: vi.fn(), remove: vi.fn() },
  folderApi: { tree: vi.fn(), breadcrumb: vi.fn(), create: vi.fn(), rename: vi.fn(), remove: vi.fn(), sort: vi.fn(), move: vi.fn() }
}))
vi.mock('element-plus', async original => ({
  ...await original(), ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue() }
}))

const initialTree = [
  { id: 10, name: '10、物业相关合同', children: [{ id: 101, name: '合同子目录', children: [] }] },
  { id: 11, name: '11、物业节日活动方案', children: [{ id: 111, name: '活动子目录', children: [] }] },
  { id: 20, name: '待删除空目录', children: [] }
]
const clone = value => JSON.parse(JSON.stringify(value))
const pending = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const wrappers = []
async function mountPage() {
  const wrapper = mount(Doc, { global: { stubs: {
    ElButton: true, ElIcon: true, ElInput: true, ElTable: true, ElTableColumn: true,
    ElDropdown: true, ElDropdownMenu: true, ElDropdownItem: true, ElTooltip: true,
    ElDialog: true, ElPagination: true, ElTreeSelect: true,
    Search: true, Plus: true, Upload: true, FolderOpened: true, Folder: true,
    MoreFilled: true, UploadFilled: true, FolderFilled: true, Sort: true, Document: true
  } } })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
function externalDrop(files) {
  const event = new Event('drop', { bubbles: true, cancelable: true })
  Object.defineProperty(event, 'dataTransfer', { value: { types: ['Files'], files } })
  return event
}

beforeEach(() => {
  vi.clearAllMocks()
  project = reactive({ currentProjectId: 1 })
  folderApi.tree.mockImplementation(async () => clone(initialTree))
  folderApi.breadcrumb.mockImplementation(async id => [{ id, name: `目录${id}` }])
  archiveApi.page.mockResolvedValue({ records: [], total: 0 })
  archiveApi.upload.mockResolvedValue({ id: 99 })
  archiveApi.add.mockResolvedValue()
  folderApi.remove.mockResolvedValue()
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('资料库目录和异步视图一致性', () => {
  it('使用真实树收起10后点击11、返回上级和刷新都不会重新展开10', async () => {
    const wrapper = await mountPage()
    const tree = wrapper.findComponent(ElTree)
    expect(tree.vm.getNode(10).expanded).toBe(true)
    await wrapper.get('.el-tree-node[data-key="10"] > .el-tree-node__content .el-tree-node__expand-icon').trigger('click')
    expect(tree.vm.getNode(10).expanded).toBe(false)
    await wrapper.get('.el-tree-node[data-key="11"] > .el-tree-node__content .tname').trigger('click')
    await flushPromises()
    expect(wrapper.vm.currentFolderId).toBe(11)
    expect(tree.vm.getNode(10).expanded).toBe(false)
    await wrapper.vm.selectFolder(111)
    await wrapper.vm.selectFolder(11)
    await wrapper.vm.refresh()
    await flushPromises()
    expect(tree.vm.getNode(10).expanded).toBe(false)
    expect(tree.vm.getNode(11).expanded).toBe(true)
    expect(tree.vm.getCurrentKey()).toBe(11)
  })

  it.each([true, false])('快速切换目录时旧响应先完成=%s，均不能覆盖新目录或提前结束加载', async oldFirst => {
    const wrapper = await mountPage()
    const oldFiles = pending(), newFiles = pending(), oldPath = pending(), newPath = pending()
    archiveApi.page.mockImplementation(({ folderId }) => folderId === 10 ? oldFiles.promise : newFiles.promise)
    folderApi.breadcrumb.mockImplementation(id => id === 10 ? oldPath.promise : newPath.promise)
    const first = wrapper.vm.selectFolder(10)
    const second = wrapper.vm.selectFolder(11)
    const finishOld = () => { oldFiles.resolve({ records: [{ id: 1, name: '旧目录文件' }], total: 1 }); oldPath.resolve([{ id: 10, name: '旧路径' }]) }
    const finishNew = () => { newFiles.resolve({ records: [{ id: 2, name: '新目录文件' }], total: 1 }); newPath.resolve([{ id: 11, name: '新路径' }]) }
    if (oldFirst) {
      finishOld(); await first
      expect(wrapper.vm.loading).toBe(true)
      expect(wrapper.vm.files).toEqual([])
      expect(wrapper.vm.breadcrumb.at(-1).id).toBe(11)
      finishNew()
    } else {
      finishNew(); await second
      finishOld()
    }
    await Promise.all([first, second])
    expect(wrapper.vm.files.map(row => row.id)).toEqual([2])
    expect(wrapper.vm.breadcrumb).toEqual([{ id: 11, name: '新路径' }])
    expect(wrapper.vm.currentFolderId).toBe(11)
    expect(wrapper.vm.loading).toBe(false)
  })

  it('切园区延续全局资料库设计，旧目录与旧刷新响应不能覆盖回根后的视图', async () => {
    const wrapper = await mountPage()
    const oldFiles = pending(), oldPath = pending(), oldTree = pending()
    archiveApi.page.mockImplementation(({ folderId }) => folderId === 10 ? oldFiles.promise : Promise.resolve({ records: [{ id: 2, name: '根目录文件' }], total: 1 }))
    folderApi.breadcrumb.mockReturnValue(oldPath.promise)
    const first = wrapper.vm.selectFolder(10)
    folderApi.tree.mockReturnValueOnce(oldTree.promise)
    const oldRefresh = wrapper.vm.refresh()
    project.currentProjectId = 2
    await nextTick(); await flushPromises()
    const reads = archiveApi.page.mock.calls.length
    oldTree.resolve([{ id: 30, name: '过时的目录响应' }])
    oldFiles.resolve({ records: [{ id: 1, name: '过时文件' }], total: 1 })
    oldPath.resolve([{ id: 10, name: '过时路径' }])
    await Promise.all([first, oldRefresh]); await flushPromises()
    expect(wrapper.vm.currentFolderId).toBe(null)
    expect(wrapper.vm.breadcrumb).toEqual([])
    expect(wrapper.vm.files.map(row => row.id)).toEqual([2])
    expect(wrapper.vm.folderTree.map(row => row.id)).toEqual([10, 11, 20])
    expect(archiveApi.page.mock.calls.length).toBe(reads)
    expect(folderApi.tree.mock.calls.every(args => args.length === 0)).toBe(true)
  })

  it('删除正在查看的空目录会刷新树、回根目录并清掉已删除节点', async () => {
    const wrapper = await mountPage()
    await wrapper.vm.selectFolder(20)
    folderApi.tree.mockResolvedValue(clone(initialTree.slice(0, 2)))
    await wrapper.vm.removeFolder(initialTree[2])
    await flushPromises()
    expect(wrapper.vm.currentFolderId).toBe(null)
    expect(wrapper.vm.breadcrumb).toEqual([])
    expect(wrapper.vm.folderTree.some(node => node.id === 20)).toBe(false)
    expect(wrapper.find('.el-tree-node[data-key="20"]').exists()).toBe(false)
    expect(archiveApi.page).toHaveBeenLastCalledWith(expect.objectContaining({ folderId: 0, pageNo: 1 }))
  })

  it('删除末页最后一项后回到剩余的有效页', async () => {
    const wrapper = await mountPage()
    wrapper.vm.query.pageNo = 2
    archiveApi.page.mockImplementation(async ({ pageNo }) => pageNo === 2
      ? { records: [], total: 20 } : { records: [{ id: 8, name: '上一页资料' }], total: 20 })
    await wrapper.vm.removeDoc({ id: 9, name: '末页资料' })
    expect(wrapper.vm.query.pageNo).toBe(1)
    expect(wrapper.vm.files.map(row => row.id)).toEqual([8])
    expect(wrapper.vm.total).toBe(20)
    expect(wrapper.vm.loading).toBe(false)
  })

  it('连续搜索时旧结果不能覆盖新关键词的结果', async () => {
    const wrapper = await mountPage()
    const first = pending(), second = pending()
    archiveApi.page.mockImplementation(({ keyword }) => keyword === '旧' ? first.promise : second.promise)
    wrapper.vm.keyword = '旧'; wrapper.vm.search()
    wrapper.vm.keyword = '新'; wrapper.vm.search()
    second.resolve({ records: [{ id: 2, name: '新结果' }], total: 1 }); await flushPromises()
    first.resolve({ records: [{ id: 1, name: '旧结果' }], total: 1 }); await flushPromises()
    expect(wrapper.vm.files.map(row => row.id)).toEqual([2])
    expect(wrapper.vm.loading).toBe(false)
  })
})

describe('资料库拖放和保存不重复执行', () => {
  it('文件拖到子目录卡片只上传一次，不冒泡上传到父目录', async () => {
    const wrapper = await mountPage()
    const event = externalDrop([new File(['内容'], '合同.pdf')])
    wrapper.findAll('.fold-card')[0].element.dispatchEvent(event)
    await flushPromises()
    expect(event.defaultPrevented).toBe(true)
    expect(archiveApi.upload).toHaveBeenCalledTimes(1)
    expect(archiveApi.add).toHaveBeenCalledTimes(1)
    expect(archiveApi.add).toHaveBeenCalledWith({ folderId: 10, fileId: 99, name: '合同' })
  })

  it('普通文本和目录拖动不触发上传遮罩', async () => {
    const wrapper = await mountPage()
    await wrapper.get('.main').trigger('dragover', { dataTransfer: { types: ['text/plain'], files: [] } })
    expect(wrapper.vm.dragOver).toBe(false)
    expect(wrapper.find('.drop-mask').exists()).toBe(false)
  })

  it('上传中追加的合法批量按原目标目录串行处理，不并发启动也不丢文件', async () => {
    const wrapper = await mountPage()
    const first = pending()
    archiveApi.upload.mockReturnValueOnce(first.promise).mockResolvedValueOnce({ id: 2 }).mockResolvedValueOnce({ id: 3 })
    const running = wrapper.vm.uploadFiles([new File(['a'], '甲.txt'), new File(['b'], '乙.txt')], 10)
    await wrapper.vm.uploadFiles([new File(['c'], '丙.txt')], 11)
    expect(archiveApi.upload).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.uploading).toBe(true)
    first.resolve({ id: 1 }); await running
    expect(archiveApi.upload).toHaveBeenCalledTimes(3)
    expect(archiveApi.add.mock.calls.map(([body]) => [body.folderId, body.fileId])).toEqual([[10, 1], [10, 2], [11, 3]])
    expect(wrapper.vm.uploading).toBe(false)
  })

  it('新建目录的回车和保存按钮只能发一次，失败后可以继续修正重试', async () => {
    const wrapper = await mountPage()
    const saving = pending()
    folderApi.create.mockReturnValueOnce(saving.promise).mockResolvedValueOnce()
    wrapper.vm.openFolderDialog(10)
    wrapper.vm.folderDialog.name = '新目录'
    const first = wrapper.vm.saveFolder()
    await wrapper.vm.saveFolder()
    wrapper.vm.openRenameFolder({ id: 11, name: '其他目录' })
    expect(folderApi.create).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.folderDialog.mode).toBe('create')
    expect(wrapper.vm.folderDialog.saving).toBe(true)
    saving.reject(new Error('稍后重试')); await first
    expect(wrapper.vm.folderDialog.visible).toBe(true)
    expect(wrapper.vm.folderDialog.saving).toBe(false)
    await wrapper.vm.saveFolder()
    expect(folderApi.create).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.folderDialog.visible).toBe(false)
  })

  it.each(['rename', 'move'])('%s 保存中不能重复提交或换成另一条资料，完成后恢复操作', async mode => {
    const wrapper = await mountPage()
    const request = pending(), row = { id: 1, name: '原文件', folderId: 10 }
    const api = mode === 'rename' ? archiveApi.update : archiveApi.move
    api.mockReturnValueOnce(request.promise)
    const open = mode === 'rename' ? wrapper.vm.openRenameDoc : wrapper.vm.openMove
    const save = mode === 'rename' ? wrapper.vm.saveRenameDoc : wrapper.vm.saveMove
    const dialog = mode === 'rename' ? wrapper.vm.renameDocDialog : wrapper.vm.moveDialog
    open(row)
    if (mode === 'rename') dialog.name = '新名称'
    else dialog.folderId = 11
    const running = save()
    await save()
    open({ id: 2, name: '另一资料', folderId: 11 })
    expect(api).toHaveBeenCalledTimes(1)
    expect(mode === 'rename' ? dialog.row.id : dialog.rows[0].id).toBe(1)
    expect(dialog.saving).toBe(true)
    request.resolve(); await running
    expect(dialog.saving).toBe(false)
    expect(dialog.visible).toBe(false)
  })
})
