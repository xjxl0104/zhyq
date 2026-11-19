const businessDate = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit'
})

export function parkToday(date = new Date()) {
  return businessDate.format(date)
}
