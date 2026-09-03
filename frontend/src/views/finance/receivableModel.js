const money = (value) => value == null ? '-' : Number(value).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 6
})
const area = (value) => value == null ? '-' : Number(value).toLocaleString('zh-CN', {
  maximumFractionDigits: 4
})

// 用 minWidth(而非固定 width):列可随内容/容器伸缩,且用户可拖拽列边框调宽,内容不被硬截断。
// fixed 只保留 序号 + 租户 两列,避免钉太多列挤死中间可视区。
export const receivableColumns = [
  { label: '序号', prop: 'seqNo', minWidth: 72, fixed: 'left' },
  { label: '协议编号', prop: 'agreementNoRaw', minWidth: 160 },
  { label: '租户', prop: 'tenantNameRaw', minWidth: 200, fixed: 'left' },
  { label: '楼层', prop: 'spaceNameRaw', minWidth: 200 },
  { label: '计租总面积/方', prop: 'chargeArea', minWidth: 140, format: area, align: 'right' },
  { label: '其中:实际房产面积', prop: 'actualArea', minWidth: 160, format: area, align: 'right' },
  { label: '其中:分摊面积', prop: 'sharedArea', minWidth: 140, format: area, align: 'right' },
  { label: '合同年限', prop: 'contractTermRaw', minWidth: 110 },
  { label: '合同租金总金额', prop: 'contractRentTotal', minWidth: 170, format: money, align: 'right' },
  { label: '签约期限', prop: 'contractPeriodRaw', minWidth: 190 },
  { label: '递增年限及幅度（租金+物业）', prop: 'escalationRaw', minWidth: 220 },
  { label: '免租期', prop: 'freeTermRaw', minWidth: 110 },
  { label: '免租期限', prop: 'freePeriodRaw', minWidth: 200 },
  { label: '优惠期/备注', prop: 'discountRaw', minWidth: 220 },
  { label: '租金', prop: 'rentRateRaw', minWidth: 190 },
  { label: '物业管理费', prop: 'propertyRateRaw', minWidth: 190 },
  { label: '月租金/元', prop: 'monthlyRent', minWidth: 140, format: money, align: 'right' },
  { label: '月物业费/元', prop: 'monthlyProperty', minWidth: 140, format: money, align: 'right' },
  { label: '月租金物业总计', prop: 'monthlyTotal', minWidth: 160, format: money, align: 'right' },
  { label: '租金保证金', prop: 'rentDeposit', minWidth: 140, format: money, align: 'right' },
  { label: '物业保证金', prop: 'propertyDeposit', minWidth: 140, format: money, align: 'right' },
  { label: '收款时间', prop: 'collectionTimingRaw', minWidth: 220 },
  { label: '开始收取租金时间', prop: 'firstCollectionRaw', minWidth: 180 },
  // 收缴政策字段:该日之前不计滞纳金(逾期状态照标),空 = 默认口径
  { label: '滞纳金起算日', prop: 'lateFeeStartDate', minWidth: 130 },
  { label: '租金收款账户', prop: 'rentAccountMasked', minWidth: 260 },
  { label: '物业管理、水电收款账户', prop: 'propertyAccountMasked', minWidth: 280 },
  { label: '备注', prop: 'notesRaw', minWidth: 240 },
  { label: '保证金差额', prop: 'depositDifference', minWidth: 140, format: money, align: 'right' }
]

export function formatReceivableCell(row, column) {
  const value = row?.[column.prop]
  if (column.format) return column.format(value)
  return value == null || value === '' ? '-' : value
}

export function canConfirmReceivableImport(preview) {
  if (!preview || Number(preview.invalidRows || 0) > 0 || preview.totalsReconciled === false) return false
  return !(preview.rows || []).some(row => ['INVALID', 'NEEDS_BINDING', 'PENDING_BINDING'].includes(row.status))
}

const nullablePositiveId = (value) => {
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? number : null
}

export function buildReceivableBinding(row) {
  return {
    tenantRefId: nullablePositiveId(row?.tenantRefId),
    spaceId: nullablePositiveId(row?.spaceId),
    roomId: nullablePositiveId(row?.roomId),
    contractId: nullablePositiveId(row?.contractId)
  }
}
