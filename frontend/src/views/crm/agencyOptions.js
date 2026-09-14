// 中介管理的下拉选项：中介台账页与中介跟进抽屉共用，加选项只改这一处。

export const AGENCY_TYPE_OPTIONS = ['中介公司', '个人经纪人', '物流/电商服务商', '商会/协会', '其他']

export const AGENCY_GRADE_OPTIONS = ['A-核心合作', 'B-一般合作', 'C-潜在合作']

export const AGENCY_FOLLOW_TYPE_OPTIONS = ['电话', '微信/在线沟通', '上门拜访', '带客看仓', '邮件', '其他']

export const COOP_CHANGE_OPTIONS = ['明显升温', '持平', '降温', '暂停合作']

/** 等级配色：A 红(重点维护) B 橙 C 灰 */
export const agencyGradeType = (g) => {
  if (!g) return 'info'
  if (g.startsWith('A')) return 'danger'
  if (g.startsWith('B')) return 'warning'
  return 'info'
}
