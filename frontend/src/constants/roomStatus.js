/**
 * 房源状态统一定义（批次③-4）。
 * 消除 SectionView 与 RoomControl 各存一份房态映射的重复。规格 §17，状态 0~8。
 */
export const ROOM_STATUS = {
  0: { label: '未配置', color: '#94a3b8', tagType: 'info' },
  1: { label: '可租', color: '#16a34a', tagType: 'success' },
  2: { label: '锁定', color: '#f59e0b', tagType: 'warning' },
  3: { label: '意向占用', color: '#f59e0b', tagType: 'warning' },
  4: { label: '签约中', color: '#f59e0b', tagType: 'warning' },
  5: { label: '在租', color: '#4f46e5', tagType: 'primary' },
  6: { label: '退租处理中', color: '#e5484d', tagType: 'danger' },
  7: { label: '维修', color: '#e5484d', tagType: 'danger' },
  8: { label: '停用', color: '#e5484d', tagType: 'danger' }
}

/** 图例（合并同色状态） */
export const ROOM_STATUS_LEGENDS = [
  { label: '可租', color: '#16a34a' },
  { label: '锁定/意向/签约中', color: '#f59e0b' },
  { label: '在租', color: '#4f46e5' },
  { label: '退租/维修/停用', color: '#e5484d' },
  { label: '未配置', color: '#94a3b8' }
]

export const roomStatusLabel = (s) => (ROOM_STATUS[s] || ROOM_STATUS[0]).label
export const roomStatusColor = (s) => (ROOM_STATUS[s] || ROOM_STATUS[0]).color
export const roomStatusTagType = (s) => (ROOM_STATUS[s] || ROOM_STATUS[0]).tagType
