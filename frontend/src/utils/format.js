// 金额格式化的单一真相源。
//
// 会计专用格式:千分位分隔 + 固定两位小数,如 3533300 → "3,533,300.00"。
// 让金额一眼可读,不用数位数。展示前一律用 ¥ 前缀,由模板负责(本函数只出数字)。
//
// 空值(null/undefined/空串)与非数值按 0.00 显示,避免账单页出现 "NaN"。
// 负数保留负号(如红冲单 -9,400.00),模板另做红色样式。
//
// 注意:登记表里的单价/面积等需要更高精度(最多 6 位小数)且空值显示 "-",
// 那是另一套口径,见 views/finance/receivableModel.js,不要并到这里。
export function money(value) {
  if (value === null || value === undefined || value === '') return '0.00'
  const num = Number(value)
  if (Number.isNaN(num)) return '0.00'
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}
