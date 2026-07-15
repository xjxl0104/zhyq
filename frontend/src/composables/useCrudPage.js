import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 标准列表页 CRUD 封装（批次③ P0-3 #14）。
 *
 * 消除每个 list 页重复的样板：loading/list/total/query 状态、load()/reset()、
 * 分页绑定、新增/编辑 dialog 的 visible/title/表单重置/add-vs-edit 分支提交、
 * 带确认的删除。列模板、表单字段、自定义动作（如审批/归档）仍由页面自己写，
 * 只把流程性样板抽出来。
 *
 * 用法：
 *   const {
 *     loading, list, total, query, load, reset,
 *     dialogVisible, dialogTitle, form, isEdit,
 *     openAdd, openEdit, submit, remove
 *   } = useCrudPage(projectApi, {
 *     defaultQuery: { name: '', status: null },
 *     emptyForm: () => ({ id: null, code: '', name: '', status: 1 }),
 *     titles: { add: '新建项目', edit: '编辑项目' }
 *   })
 *
 * 迁移页里若已有 `dialog.visible` / `dialog.title` 这类模板绑定，可用
 *   const dialog = reactive({ visible: dialogVisible, title: dialogTitle })
 * 做别名（reactive 会自动解包顶层 ref），无需改模板。
 *
 * @param {object} api - 形如 {page, get, add, update, remove} 的 api 模块；
 *   缺某个方法时对应能力自动禁用（跳过调用），不会报错。
 * @param {object} [options]
 * @param {object} [options.defaultQuery] - 查询条件初始值（不含 pageNo/pageSize，会自动补上）
 * @param {() => object} [options.emptyForm] - 返回新增表单的空对象，默认 () => ({})
 * @param {(form: object) => (object|Promise<object>|void)} [options.beforeSubmit] - 提交前钩子
 *   （校验/加工），可返回替换后的表单数据用于提交；不返回则原样提交当前 form。
 * @param {string} [options.pageField] - 分页响应里记录数组的字段名，兼容后端不同形状；
 *   未指定时依次尝试 records / list。
 * @param {number} [options.pageSize] - 初始每页条数，默认 10。
 * @param {{add?: string, edit?: string}} [options.titles] - 新增/编辑 dialog 标题文案。
 */
export function useCrudPage(api, options = {}) {
  const {
    defaultQuery = {},
    emptyForm = () => ({}),
    beforeSubmit,
    pageField,
    pageSize = 10,
    titles = {}
  } = options

  const loading = ref(false)
  const list = ref([])
  const total = ref(0)
  const query = reactive({ pageNo: 1, pageSize, ...defaultQuery })

  function extractRecords(res) {
    if (Array.isArray(res)) return res
    if (pageField) return res?.[pageField] ?? []
    return res?.records ?? res?.list ?? []
  }

  async function load() {
    if (!api.page) return
    loading.value = true
    try {
      const res = await api.page(query)
      list.value = extractRecords(res)
      total.value = res?.total ?? 0
    } finally {
      loading.value = false
    }
  }

  function reset() {
    Object.assign(query, { pageNo: 1, pageSize }, defaultQuery)
    load()
  }

  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const isEdit = ref(false)
  const form = reactive(emptyForm())

  function openAdd() {
    isEdit.value = false
    dialogTitle.value = titles.add ?? '新增'
    Object.assign(form, emptyForm())
    dialogVisible.value = true
  }

  function openEdit(row) {
    isEdit.value = true
    dialogTitle.value = titles.edit ?? '编辑'
    Object.assign(form, emptyForm(), row)
    dialogVisible.value = true
  }

  async function submit() {
    let payload = form
    if (beforeSubmit) {
      const result = await beforeSubmit(form)
      if (result && typeof result === 'object') payload = result
    }
    if (isEdit.value) {
      if (!api.update) return
      await api.update(payload)
    } else {
      if (!api.add) return
      await api.add(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  }

  async function remove(row) {
    if (!api.remove) return
    try {
      await ElMessageBox.confirm('确认删除?', '提示', { type: 'warning' })
    } catch {
      return // 用户取消
    }
    await api.remove(row.id)
    ElMessage.success('删除成功')
    await load()
  }

  return {
    loading,
    list,
    total,
    query,
    load,
    reset,
    dialogVisible,
    dialogTitle,
    form,
    isEdit,
    openAdd,
    openEdit,
    submit,
    remove
  }
}
