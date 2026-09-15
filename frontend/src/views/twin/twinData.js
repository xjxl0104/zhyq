// Storey heights come from the supplied brochure; footprint and device placements remain illustrative.
export const PARK_REFERENCE = Object.freeze({ name: 'DIPARK 数智云仓产业园', totalArea: '82,000', freightLifts: 19, address: '广州市花都区炭步镇', source: '招商资料' })
export const MODEL = Object.freeze({ width: 96, depth: 54, floorHeight: 6.6, floors: 7, baseHeight: 12, secondFloorHeight: 9.5 })
export const floorBase = floor => floor === 1 ? 0 : floor === 2 ? MODEL.baseHeight : MODEL.baseHeight + MODEL.secondFloorHeight + (floor - 3) * MODEL.floorHeight
export const floorHeight = floor => floor === 1 ? MODEL.baseHeight : floor === 2 ? MODEL.secondFloorHeight : MODEL.floorHeight
export const modelHeight = () => floorBase(MODEL.floors) + floorHeight(MODEL.floors)
export const FLOORS = Array.from({ length: MODEL.floors }, (_, i) => ({
  id: i + 1, label: (i + 1) + 'F', name: i === 0 ? '装卸与物流中心' : i === MODEL.floors - 1 ? '仓储与设备层' : '标准仓储空间',
  area: '5,184', occupancy: [76, 92, 88, 100, 84, 72, 65][i],
}))
export const MODULES = [
  { id: 'park', name: '园区空间', short: '园区', icon: 'building', color: '#148975', route: '/building/project', metric: String(MODEL.floors), unit: '个楼层', description: '建筑、楼层与房源，在空间里一目了然。' },
  { id: 'property', name: '物业服务', short: '物业', icon: 'tool', color: '#387bd5', route: '/property/workorder', metric: '12', unit: '项待办', description: '从设备位置出发，跟进报修、巡检与维护。' },
  { id: 'contract', name: '租赁合同', short: '合同', icon: 'document', color: '#9a77cc', route: '/contract/list', metric: '36', unit: '份在执行', description: '让每一份合同，对应到真实的仓储空间。' },
  { id: 'camera', name: '视频监控', short: '监控', icon: 'camera', color: '#168ca5', route: '/iot/camera', metric: '48', unit: '个监控点', description: '按楼层定位摄像头，查看通道与设备信息。' },
  { id: 'fire', name: '智慧消防', short: '消防', icon: 'fire', color: '#dd705a', route: '/iot/fire', metric: '128', unit: '个消防点', description: '从消防管网到消火栓，关联巡检和安消设备。' },
  { id: 'energy', name: '能源管理', short: '能源', icon: 'bolt', color: '#c49b41', route: '/energy/meter', metric: '24', unit: '台智能表计', description: '将用能设备落到空间，连接表计与抄表记录。' },
]
export const POINTS = [
  { id: 'park-01', module: 'park', name: 'DIPARK · 数智云仓', floor: 7, position: [-25, 57, 0], detail: '7 层空间模型 · 参照实景与招商资料', code: 'BLD-01', status: '空间档案', location: '数智云仓产业园 / 云仓 01', values: [['资料来源', '用户提供的招商资料'], ['层高', '12 / 9.5 / 6.6 m'], ['园区总面积', '8.2 万 m²（资料）'], ['货梯配置', '19 台（资料）']] },
  { id: 'camera-01', module: 'camera', name: '东侧装卸区监控', floor: 1, position: [49, 5, 19], detail: 'CAM-012 · 装卸作业区', code: 'CAM-012', status: '演示在线', location: '云仓 01 / 1F / 东侧装卸区', values: [['设备类型', '网络摄像机'], ['通道编号', 'CH-012'], ['视频接入', '待连接视频平台']] },
  { id: 'fire-01', module: 'fire', name: '消防立管 · 东区', floor: 3, position: [47, floorBase(3) + 3, 28], detail: 'HYD-032 · 消火栓系统', code: 'HYD-032', status: '待巡检', location: '云仓 01 / 3F / 东区消防通道', values: [['设备类型', '室内消火栓'], ['关联系统', '消防给水管网'], ['巡检任务', '月度例行巡检']] },
  { id: 'contract-01', module: 'contract', name: '3F · A 区租赁空间', floor: 3, position: [-22, floorBase(3) + 2, 28], detail: 'A-301 · 关联租赁合同', code: 'A-301', status: '演示在租', location: '云仓 01 / 3F / A 区', values: [['示例承租方', '云链物流（演示）'], ['租赁面积', '1,296 m²（示意）'], ['合同编号', 'DEMO-2026-003']] },
  { id: 'property-01', module: 'property', name: '装卸平台 · 维保', floor: 1, position: [-28, 3, 32], detail: 'DOCK-006 · 液压升降平台', code: 'DOCK-006', status: '维保待办', location: '云仓 01 / 1F / 南侧 06 号月台', values: [['资产类型', '液压升降平台'], ['工单内容', '平台例行维护'], ['示例负责人', '物业工程组']] },
  { id: 'energy-01', module: 'energy', name: '屋顶 · 设备机房', floor: 7, position: [25, modelHeight() + 4, -7], detail: 'EM-008 · 公共区域用电', code: 'EM-008', status: '演示在线', location: '云仓 01 / 屋顶 / 设备机房', values: [['表计类型', '三相智能电表'], ['计量范围', '公共动力用电'], ['数据接入', '待连接智能表计']] },
]
export const REFERENCES = [
  { id: 11, title: '园区主入口 · 门楣与岗亭', extension: 'jpg' },
  { id: 10, title: '建成实景 · 标识与景观', extension: 'jpg' }, { id: 9, title: '招商资料 · 楼层与层高', extension: 'jpg' },
  { id: 8, title: '建筑外观 · 青蓝转角' }, { id: 6, title: '园区鸟瞰 · 建筑体量' },
  { id: 7, title: '横向立面 · 连续窗带' }, { id: 5, title: '首层 · 装卸月台' },
  { id: 1, title: '室内 · 柱网与空间' }, { id: 2, title: '室内 · 风管与消防' },
  { id: 3, title: '室内 · 消火栓与立管' }, { id: 4, title: '转角 · 幕墙细部' },
]
export function moduleDestination(id, preview = false) {
  const module = MODULES.find(item => item.id === id)
  if (!module) return null
  return preview ? '/twin-preview/module/' + id : module.route
}
export function visiblePoints(layer, floor, mode) {
  return POINTS.filter(point => (layer === 'all' || layer === point.module) &&
    (floor == null || point.floor === floor || mode === 'exterior'))
}
