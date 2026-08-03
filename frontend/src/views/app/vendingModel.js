export const vendingTypes = [
  {
    name: 'MACHINE', label: '机器',
    columns: [
      ['vendorMachineId', '厂商机器编号', 150], ['machineName', '机器名称', 140],
      ['siteName', '点位', 180], ['model', '型号', 130], ['runningStatus', '运行状态', 100],
      ['lastOnlineTime', '最后在线时间', 180]
    ]
  },
  {
    name: 'SALE', label: '销售',
    columns: [
      ['vendorOrderId', '厂商订单号', 170], ['lineNo', '行号', 70],
      ['vendorMachineId', '机器编号', 130], ['productName', '商品名称', 150], ['quantity', '数量', 80],
      ['originalAmount', '原价金额', 110, 'money'], ['discountAmount', '优惠金额', 110, 'money'],
      ['paidAmount', '实付金额', 110, 'money'], ['paymentMethod', '支付方式', 100],
      ['paymentTime', '支付时间', 180], ['orderStatus', '订单状态', 100]
    ]
  },
  {
    name: 'RESTOCK', label: '补货',
    columns: [
      ['vendorRestockId', '厂商补货单号', 170], ['vendorMachineId', '机器编号', 130],
      ['productName', '商品名称', 150], ['quantity', '补货数量', 100],
      ['operatorName', '补货人', 100], ['restockTime', '补货时间', 180]
    ]
  },
  {
    name: 'FAULT', label: '故障',
    columns: [
      ['vendorFaultId', '厂商故障编号', 170], ['vendorMachineId', '机器编号', 130],
      ['faultType', '故障类型', 130], ['occurredTime', '发生时间', 180],
      ['recoveredTime', '恢复时间', 180], ['faultStatus', '状态', 100], ['description', '描述', 220]
    ]
  },
  {
    name: 'RECONCILIATION', label: '对账',
    columns: [
      ['vendorSettlementId', '厂商结算单号', 180], ['periodStart', '周期开始', 120],
      ['periodEnd', '周期结束', 120], ['salesAmount', '销售总额', 120, 'money'],
      ['refundAmount', '退款', 110, 'money'], ['platformFee', '平台费用', 110, 'money'],
      ['netAmount', '结算净额', 120, 'money'], ['settlementStatus', '状态', 100]
    ]
  }
]

export function openExternalVending(url, opener = window.open) {
  let parsed
  try {
    parsed = new URL(url)
  } catch {
    throw new Error('厂商入口地址无效')
  }
  if (parsed.protocol !== 'https:') throw new Error('厂商入口必须使用 HTTPS')
  return opener(url, '_blank', 'noopener,noreferrer')
}

export function canConfirmVendingImport(preview) {
  return Boolean(preview && Number(preview.validRows) > 0 && Number(preview.invalidRows) === 0)
}

export function formatVendingCell(row, column) {
  const value = row?.[column[0]]
  if (value === null || value === undefined || value === '') return '—'
  if (column[3] === 'money') {
    return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  }
  return value
}
