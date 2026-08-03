const money = (value) => value == null ? '-' : Number(value).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 6
})
const area = (value) => value == null ? '-' : Number(value).toLocaleString('zh-CN', {
  maximumFractionDigits: 4
})

export const receivableColumns = [
  { label: '序号', prop: 'seqNo', width: 72, fixed: 'left' },
  { label: '协议编号', prop: 'agreementNoRaw', width: 150, fixed: 'left' },
  { label: '租户', prop: 'tenantNameRaw', width: 180, fixed: 'left' },
  { label: '楼层', prop: 'spaceNameRaw', width: 150, fixed: 'left' },
  { label: '计租总面积/方', prop: 'chargeArea', width: 140, format: area, align: 'right' },
  { label: '其中:实际房产面积', prop: 'actualArea', width: 160, format: area, align: 'right' },
  { label: '其中:分摊面积', prop: 'sharedArea', width: 140, format: area, align: 'right' },
  { label: '合同年限', prop: 'contractTermRaw', width: 110 },
  { label: '合同租金总金额', prop: 'contractRentTotal', width: 170, format: money, align: 'right' },
  { label: '签约期限', prop: 'contractPeriodRaw', width: 190 },
  { label: '递增年限及幅度（租金+物业）', prop: 'escalationRaw', width: 220 },
  { label: '免租期', prop: 'freeTermRaw', width: 110 },
  { label: '免租期限', prop: 'freePeriodRaw', width: 200 },
  { label: '优惠期/备注', prop: 'discountRaw', width: 220 },
  { label: '租金', prop: 'rentRateRaw', width: 190 },
  { label: '物业管理费', prop: 'propertyRateRaw', width: 190 },
  { label: '月租金/元', prop: 'monthlyRent', width: 140, format: money, align: 'right' },
  { label: '月物业费/元', prop: 'monthlyProperty', width: 140, format: money, align: 'right' },
  { label: '月租金物业总计', prop: 'monthlyTotal', width: 160, format: money, align: 'right' },
  { label: '租金保证金', prop: 'rentDeposit', width: 140, format: money, align: 'right' },
  { label: '物业保证金', prop: 'propertyDeposit', width: 140, format: money, align: 'right' },
  { label: '收款时间', prop: 'collectionTimingRaw', width: 220 },
  { label: '开始收取租金时间', prop: 'firstCollectionRaw', width: 180 },
  { label: '租金收款账户', prop: 'rentAccountMasked', width: 260 },
  { label: '物业管理、水电收款账户', prop: 'propertyAccountMasked', width: 280 },
  { label: '备注', prop: 'notesRaw', width: 240 },
  { label: '保证金差额', prop: 'depositDifference', width: 140, format: money, align: 'right' }
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
