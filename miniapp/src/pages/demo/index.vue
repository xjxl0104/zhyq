<template>
  <view class="page-wrap demo-page">
    <view class="demo-topbar">
      <view>
        <view class="demo-kicker">TEMPORARY PREVIEW</view>
        <view class="demo-mode"><view class="demo-live-dot" />体验模式 · 演示数据</view>
      </view>
      <button class="demo-exit" @click="exit">退出</button>
    </view>

    <view class="demo-hero">
      <view class="demo-hero-glow" />
      <view class="demo-eyebrow">DIPARK / PRODUCT TOUR</view>
      <view class="demo-title">先体验，再登录</view>
      <view class="demo-subtitle">浏览工作台、客户和业务流程，所有操作只保存在当前演示页面。</view>
      <view class="demo-safe-note">不会访问或修改真实账号、客户、订单与收益</view>
    </view>

    <view class="role-switch">
      <button :class="['role-switch-item', { active: role === 'partner' }]" @click="setRole('partner')">园区伙伴</button>
      <button :class="['role-switch-item', { active: role === 'warehouse' }]" @click="setRole('warehouse')">云仓商家</button>
    </view>

    <scroll-view class="demo-nav" scroll-x :show-scrollbar="false">
      <view class="demo-nav-content">
        <view v-for="item in sections" :key="item.key" :class="['demo-nav-item', { active: section === item.key }]" @click="setSection(item.key)">
          <text class="demo-nav-icon">{{ item.icon }}</text><text>{{ item.label }}</text>
        </view>
      </view>
    </scroll-view>

    <template v-if="role === 'partner'">
      <view v-if="section === 'home'">
        <view class="demo-section-heading"><view><view class="demo-section-label">PARTNER WORKSPACE</view><view class="demo-section-title">园区伙伴工作台</view></view><text class="demo-date">本月演示</text></view>
        <view class="demo-metric-hero">
          <view class="metric-caption">累计收益（元）</view><view class="metric-number">12,860.50</view>
          <view class="metric-foot"><text>可提现 8,240.00</text><text>本月 +18.6%</text></view>
        </view>
        <view class="demo-stat-grid"><view v-for="item in partnerStats" :key="item.label" class="demo-stat"><view class="stat-icon" :class="item.tone">{{ item.icon }}</view><view class="stat-value">{{ item.value }}</view><view class="stat-label">{{ item.label }}</view></view></view>
        <view class="demo-section-heading compact"><view class="demo-section-title">快捷体验</view><text class="demo-section-link">点击卡片查看</text></view>
        <view class="demo-action-grid"><view v-for="item in partnerActions" :key="item.key" class="demo-action" @click="setSection(item.key)"><view class="action-icon" :class="item.tone">{{ item.icon }}</view><view class="action-name">{{ item.label }}</view><view class="action-desc">{{ item.desc }}</view><text class="action-arrow">›</text></view></view>
        <view class="demo-card"><view class="card-title-row"><view class="demo-card-title">最近动态</view><text class="demo-muted">示例记录</text></view><view v-for="item in partnerActivity" :key="item.title" class="activity-row"><view class="activity-mark">{{ item.icon }}</view><view class="activity-main"><view class="activity-title">{{ item.title }}</view><view class="activity-time">{{ item.time }}</view></view><view class="activity-amount">{{ item.amount }}</view></view></view>
      </view>

      <view v-else-if="section === 'customers'">
        <view class="demo-section-heading"><view><view class="demo-section-label">CUSTOMER PIPELINE</view><view class="demo-section-title">我的客户</view></view><button class="mini-action" @click="showAdd = true">＋ 新推荐</button></view>
        <view class="demo-card customer-tip"><view class="tip-mark">i</view><view><view class="tip-title">推荐客户，建立你的业务池</view><view class="demo-muted">演示提交只会新增到当前页面，退出后自动清空。</view></view></view>
        <view v-for="customer in partnerCustomers" :key="customer.id" class="demo-card customer-card" @click="selectedCustomer = customer; section = 'customer-detail'"><view class="customer-head"><view class="customer-avatar">{{ customer.name.slice(0, 1) }}</view><view class="customer-main"><view class="customer-name">{{ customer.name }}</view><view class="demo-muted">{{ customer.contact }} · {{ customer.phone }}</view></view><text :class="['status-tag', customer.statusTone]">{{ customer.status }}</text></view><view class="customer-meta"><text>{{ customer.service }}</text><text>{{ customer.updated }}</text><text class="customer-arrow">查看详情 ›</text></view></view>
        <view v-if="!partnerCustomers.length" class="demo-card empty-card">还没有演示客户，点击右上角新增一条。</view>
      </view>

      <view v-else-if="section === 'customer-detail' && selectedCustomer">
        <view class="demo-section-heading"><view><view class="demo-section-label">CUSTOMER DETAIL</view><view class="demo-section-title">客户详情</view></view><button class="mini-action" @click="section = 'customers'">返回列表</button></view>
        <view class="demo-card detail-card"><view class="detail-profile"><view class="detail-avatar">{{ selectedCustomer.name.slice(0, 1) }}</view><view><view class="detail-name">{{ selectedCustomer.name }}</view><view class="demo-muted">{{ selectedCustomer.contact }} · {{ selectedCustomer.phone }}</view></view></view><view class="detail-status"><text :class="['status-tag', selectedCustomer.statusTone]">{{ selectedCustomer.status }}</text><text class="demo-muted">{{ selectedCustomer.updated }}</text></view><view class="detail-line"><text>需求类型</text><text>{{ selectedCustomer.service }}</text></view><view class="detail-line"><text>跟进阶段</text><text>{{ selectedCustomer.stage }}</text></view><view class="detail-line"><text>预锁状态</text><text>{{ selectedCustomer.lock }}</text></view><button class="demo-disabled-button" @click="demoOnly">删除 / 延长预锁（登录后可用）</button></view>
      </view>

      <view v-else-if="section === 'income'">
        <view class="demo-section-heading"><view><view class="demo-section-label">REVENUE CENTER</view><view class="demo-section-title">收益明细</view></view><text class="demo-date">近 30 天</text></view>
        <view class="demo-income-hero"><view><view class="metric-caption">本月预计收益</view><view class="income-number">¥ 3,260.00</view></view><view class="income-trend">↗ 18.6%</view></view>
        <view class="demo-card"><view v-for="item in partnerIncome" :key="item.title" class="income-row"><view><view class="activity-title">{{ item.title }}</view><view class="activity-time">{{ item.time }}</view></view><view class="income-value">+{{ item.amount }}</view></view></view>
        <button class="demo-disabled-button" @click="demoOnly">申请提现（登录后可用）</button>
      </view>

      <view v-else-if="section === 'team'">
        <view class="demo-section-heading"><view><view class="demo-section-label">PARTNER NETWORK</view><view class="demo-section-title">我的团队</view></view><text class="demo-date">12 位成员</text></view>
        <view class="demo-team-hero"><view class="team-ring">P3</view><view><view class="team-title">金牌合伙人</view><view class="demo-muted">距离钻石合伙人还差 8 个有效客户</view></view></view>
        <view class="demo-card"><view v-for="member in partnerTeam" :key="member.name" class="team-row"><view class="team-avatar">{{ member.name.slice(0, 1) }}</view><view class="team-main"><view class="activity-title">{{ member.name }}</view><view class="activity-time">{{ member.position }} · {{ member.customers }} 个客户</view></view><text class="team-score">{{ member.score }}</text></view></view>
      </view>

      <view v-else>
        <view class="demo-section-heading"><view><view class="demo-section-label">NOTIFICATION CENTER</view><view class="demo-section-title">消息通知</view></view><text class="demo-date">3 条未读</text></view>
        <view class="demo-card"><view v-for="item in partnerNotices" :key="item.title" class="notice-row"><view class="notice-dot" :class="item.tone" /><view><view class="activity-title">{{ item.title }}</view><view class="activity-time">{{ item.time }}</view><view class="notice-copy">{{ item.copy }}</view></view></view></view>
      </view>
    </template>

    <template v-else>
      <view v-if="section === 'home'">
        <view class="demo-section-heading"><view><view class="demo-section-label">WAREHOUSE WORKSPACE</view><view class="demo-section-title">云仓商家看板</view></view><text class="demo-date">本月演示</text></view>
        <view class="warehouse-demo-hero"><view class="warehouse-status">● 运营中</view><view class="warehouse-demo-title">星河智能仓</view><view class="warehouse-demo-subtitle">加盟进度、订单与结算，一站掌握</view><view class="warehouse-progress"><view class="progress-fill" /><view class="progress-label"><text>入驻进度</text><text>72%</text></view></view></view>
        <view class="demo-stat-grid"><view v-for="item in warehouseStats" :key="item.label" class="demo-stat"><view class="stat-icon" :class="item.tone">{{ item.icon }}</view><view class="stat-value">{{ item.value }}</view><view class="stat-label">{{ item.label }}</view></view></view>
        <view class="demo-section-heading compact"><view class="demo-section-title">业务入口</view><text class="demo-section-link">演示可浏览</text></view>
        <view class="demo-action-grid"><view v-for="item in warehouseActions" :key="item.key" class="demo-action" @click="setSection(item.key)"><view class="action-icon" :class="item.tone">{{ item.icon }}</view><view class="action-name">{{ item.label }}</view><view class="action-desc">{{ item.desc }}</view><text class="action-arrow">›</text></view></view>
        <view class="demo-card"><view class="card-title-row"><view class="demo-card-title">订单动态</view><text class="demo-muted">最近更新</text></view><view v-for="item in warehouseActivity" :key="item.title" class="activity-row"><view class="activity-mark warehouse-mark">{{ item.icon }}</view><view class="activity-main"><view class="activity-title">{{ item.title }}</view><view class="activity-time">{{ item.time }}</view></view><view class="activity-amount">{{ item.status }}</view></view></view>
      </view>

      <view v-else-if="section === 'orders'">
        <view class="demo-section-heading"><view><view class="demo-section-label">ORDER CENTER</view><view class="demo-section-title">出库单</view></view><text class="demo-date">今日 12 单</text></view>
        <view v-for="order in warehouseOrders" :key="order.id" class="demo-card order-card"><view class="card-title-row"><view class="order-id">{{ order.id }}</view><text :class="['status-tag', order.tone]">{{ order.status }}</text></view><view class="order-info"><view><view class="demo-muted">客户</view><view class="activity-title">{{ order.customer }}</view></view><view><view class="demo-muted">件数</view><view class="activity-title">{{ order.count }} 件</view></view><view><view class="demo-muted">更新时间</view><view class="activity-title">{{ order.time }}</view></view></view><button class="demo-disabled-button" @click="demoOnly">查看订单详情（登录后可用）</button></view>
      </view>

      <view v-else-if="section === 'erp'">
        <view class="demo-section-heading"><view><view class="demo-section-label">ERP CONNECTOR</view><view class="demo-section-title">ERP 接入</view></view><text class="demo-date">连接器 3 个</text></view>
        <view class="demo-card erp-card"><view v-for="item in warehouseErp" :key="item.name" class="erp-row"><view class="erp-icon">{{ item.icon }}</view><view class="erp-main"><view class="activity-title">{{ item.name }}</view><view class="activity-time">{{ item.desc }}</view></view><text :class="['status-tag', item.tone]">{{ item.status }}</text></view></view>
        <button class="demo-disabled-button" @click="demoOnly">新建 ERP 凭证（登录后可用）</button>
      </view>

      <view v-else-if="section === 'settlement'">
        <view class="demo-section-heading"><view><view class="demo-section-label">SETTLEMENT CENTER</view><view class="demo-section-title">结算单</view></view><text class="demo-date">本月</text></view>
        <view class="warehouse-settle-hero"><view class="metric-caption">待结算金额</view><view class="metric-number">¥ 86,420.00</view><view class="metric-foot"><text>已结算 ¥ 248,600.00</text><text>共 24 单</text></view></view>
        <view class="demo-card"><view v-for="item in warehouseSettlement" :key="item.title" class="income-row"><view><view class="activity-title">{{ item.title }}</view><view class="activity-time">{{ item.time }}</view></view><view><view class="income-value">{{ item.amount }}</view><view class="demo-muted">{{ item.status }}</view></view></view></view>
        <button class="demo-disabled-button" @click="demoOnly">确认结算（登录后可用）</button>
      </view>

      <view v-else-if="section === 'onboarding'">
        <view class="demo-section-heading"><view><view class="demo-section-label">ONBOARDING FLOW</view><view class="demo-section-title">加盟进度</view></view><text class="demo-date">4 / 6 已完成</text></view>
        <view class="demo-card timeline-card"><view v-for="item in warehouseTimeline" :key="item.title" class="timeline-row"><view :class="['timeline-dot', item.done ? 'done' : '']">{{ item.done ? '✓' : item.index }}</view><view class="timeline-main"><view class="activity-title">{{ item.title }}</view><view class="activity-time">{{ item.desc }}</view></view><text :class="['timeline-status', item.done ? 'done-text' : '']">{{ item.done ? '已完成' : '待处理' }}</text></view></view>
        <button class="demo-disabled-button" @click="demoOnly">继续提交资料（登录后可用）</button>
      </view>

      <view v-else>
        <view class="demo-section-heading"><view><view class="demo-section-label">WAREHOUSE NETWORK</view><view class="demo-section-title">消息通知</view></view><text class="demo-date">2 条未读</text></view>
        <view class="demo-card"><view v-for="item in warehouseNotices" :key="item.title" class="notice-row"><view class="notice-dot" :class="item.tone" /><view><view class="activity-title">{{ item.title }}</view><view class="activity-time">{{ item.time }}</view><view class="notice-copy">{{ item.copy }}</view></view></view></view>
      </view>
    </template>

    <view class="demo-boundary"><view class="boundary-icon">◇</view><view><view class="boundary-title">这是临时体验模式</view><view class="boundary-copy">推荐、提现、删除、提交、上传等操作不会写入系统。需要真实业务，请返回登录。</view></view></view>

    <view v-if="showAdd" class="demo-sheet-mask" @click="showAdd = false"><view class="demo-sheet" @click.stop><view class="sheet-handle" /><view class="demo-section-title">模拟推荐客户</view><view class="demo-muted sheet-note">演示数据只在当前页面有效，退出后自动清空。</view><input class="input" v-model="form.name" placeholder="客户公司 / 店铺名称 *" /><input class="input" v-model="form.contact" placeholder="联系人" /><input class="input" v-model="form.phone" type="number" maxlength="11" placeholder="联系手机 *" /><button class="btn" @click="submitDemo">加入演示客户</button><button class="sheet-cancel" @click="showAdd = false">取消</button></view></view>
    <view v-if="notice" class="demo-toast">{{ notice }}</view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'

const role = ref('partner')
const section = ref('home')
const selectedCustomer = ref(null)
const showAdd = ref(false)
const notice = ref('')
const form = reactive({ name: '', contact: '', phone: '' })

const partnerSections = [
  { key: 'home', label: '工作台', icon: '⌂' }, { key: 'customers', label: '客户', icon: '◎' },
  { key: 'income', label: '收益', icon: '↗' }, { key: 'team', label: '团队', icon: '◌' }, { key: 'notices', label: '消息', icon: '•' },
]
const warehouseSections = [
  { key: 'home', label: '看板', icon: '⌂' }, { key: 'orders', label: '订单', icon: '▤' },
  { key: 'erp', label: 'ERP', icon: '⌘' }, { key: 'settlement', label: '结算', icon: '¥' }, { key: 'onboarding', label: '加盟', icon: '✓' }, { key: 'notices', label: '消息', icon: '•' },
]
const sections = computed(() => role.value === 'partner' ? partnerSections : warehouseSections)
const partnerStats = [
  { icon: '客', label: '有效客户', value: '28', tone: 'blue' }, { icon: '锁', label: '预锁客户', value: '6', tone: 'cyan' },
  { icon: '佣', label: '待结佣金', value: '¥3.2k', tone: 'gold' }, { icon: '团', label: '团队人数', value: '12', tone: 'purple' },
]
const warehouseStats = [
  { icon: '单', label: '今日订单', value: '12', tone: 'blue' }, { icon: '仓', label: '仓储利用率', value: '78%', tone: 'cyan' },
  { icon: '结', label: '待结算', value: '¥8.6w', tone: 'gold' }, { icon: '接', label: 'ERP 接入', value: '3', tone: 'purple' },
]
const partnerActions = [
  { key: 'customers', label: '推荐客户', desc: '新增业务线索', icon: '荐', tone: 'blue' }, { key: 'income', label: '收益明细', desc: '查看佣金进度', icon: '收', tone: 'cyan' },
  { key: 'team', label: '我的团队', desc: '成员与晋升', icon: '团', tone: 'gold' }, { key: 'notices', label: '消息通知', desc: '查看重要提醒', icon: '信', tone: 'purple' },
]
const warehouseActions = [
  { key: 'orders', label: '出库单', desc: '订单与履约', icon: '单', tone: 'blue' }, { key: 'erp', label: 'ERP 接入', desc: '连接业务系统', icon: '接', tone: 'cyan' },
  { key: 'settlement', label: '结算单', desc: '对账与结算', icon: '结', tone: 'gold' }, { key: 'onboarding', label: '加盟进度', desc: '查看入驻流程', icon: '进', tone: 'purple' },
]
const partnerActivity = [
  { icon: '✓', title: '客户「远山生活」已进入跟进', time: '今天 10:24', amount: '+ ¥ 1,280.00' }, { icon: '↗', title: '一件代发客户完成签约', time: '昨天 16:08', amount: '+ ¥ 860.00' }, { icon: '•', title: '团队成员林晓新增 2 位客户', time: '9 月 26 日', amount: '团队动态' },
]
const partnerIncome = [
  { title: '远山生活 · 一件代发', time: '今天 10:24 · 可结算', amount: '1,280.00' }, { title: '拾光家居 · 仓储', time: '昨天 16:08 · 冻结中', amount: '860.00' }, { title: '禾木食品 · 仓配一体', time: '9 月 25 日 · 已结算', amount: '560.00' },
]
const partnerTeam = [
  { name: '林晓', position: '银牌合伙人', customers: 8, score: 'P2' }, { name: '周宁', position: '园区伙伴', customers: 4, score: 'P1' }, { name: '陈宇', position: '园区伙伴', customers: 3, score: 'P1' }, { name: '赵可', position: '园区伙伴', customers: 2, score: 'P1' },
]
const partnerNotices = [
  { title: '客户「远山生活」跟进状态更新', time: '今天 10:24', copy: '客户已完成初次沟通，进入方案确认阶段。', tone: 'blue' }, { title: '本月佣金结算提醒', time: '昨天 09:00', copy: '本月预计 3,260.00 元，将于月底统一结算。', tone: 'gold' }, { title: '团队新成员加入', time: '9 月 26 日', copy: '林晓已加入你的团队，快去打个招呼吧。', tone: 'cyan' },
]
const warehouseActivity = [
  { icon: '单', title: 'SO20260928018 已完成出库', time: '今天 14:20', status: '已完成' }, { icon: '↗', title: 'SO20260928017 等待拣货', time: '今天 13:46', status: '处理中' }, { icon: '结', title: '9 月结算单已生成', time: '昨天 18:00', status: '待确认' },
]
const warehouseOrders = [
  { id: 'SO20260928018', customer: '远山生活', count: 86, time: '14:20', status: '已完成', tone: 'ok' }, { id: 'SO20260928017', customer: '拾光家居', count: 42, time: '13:46', status: '拣货中', tone: 'blue' }, { id: 'SO20260928016', customer: '禾木食品', count: 128, time: '11:12', status: '待拣货', tone: 'gold' },
]
const warehouseErp = [
  { icon: '聚', name: '聚水潭 ERP', desc: '库存、订单双向同步', status: '已连接', tone: 'ok' }, { icon: '旺', name: '旺店通 ERP', desc: '订单自动拉取', status: '已连接', tone: 'ok' }, { icon: '自', name: '自研接口', desc: 'Webhook 事件推送', status: '待配置', tone: 'gold' },
]
const warehouseSettlement = [
  { title: '9 月仓储服务费', time: '账期 09.01 - 09.30', amount: '¥ 52,800.00', status: '待确认' }, { title: '9 月代发服务费', time: '账期 09.01 - 09.30', amount: '¥ 33,620.00', status: '待确认' }, { title: '8 月结算单', time: '已于 09.10 完成', amount: '¥ 76,420.00', status: '已结算' },
]
const warehouseTimeline = [
  { index: 1, title: '提交基本资料', desc: '联系人、营业执照', done: true }, { index: 2, title: '园区初审', desc: '资料已审核通过', done: true }, { index: 3, title: '签署入驻协议', desc: '电子协议已签署', done: true }, { index: 4, title: '配置 ERP 接入', desc: '已完成 2 / 3 个连接器', done: true }, { index: 5, title: '仓库现场核验', desc: '预约园区人员上门核验', done: false }, { index: 6, title: '正式营业', desc: '完成核验后开放全部能力', done: false },
]
const warehouseNotices = [
  { title: '结算单待确认', time: '今天 09:30', copy: '9 月仓储与代发服务费已生成，请登录后确认。', tone: 'gold' }, { title: 'ERP 接入状态更新', time: '昨天 17:20', copy: '聚水潭 ERP 已完成首次库存同步。', tone: 'cyan' },
]
const partnerCustomers = ref([
  { id: 1, name: '远山生活', contact: '王女士', phone: '138****6012', service: '一件代发', status: '跟进中', statusTone: 'blue', stage: '方案确认', lock: '预锁剩 5 天', updated: '今天更新' }, { id: 2, name: '拾光家居', contact: '李先生', phone: '139****7288', service: '仓储', status: '已签约', statusTone: 'ok', stage: '合同生效', lock: '有效锁定', updated: '昨天更新' }, { id: 3, name: '禾木食品', contact: '赵女士', phone: '186****4490', service: '仓配一体', status: '待跟进', statusTone: 'gold', stage: '待首次沟通', lock: '未锁定', updated: '9 月 25 日' },
])

function setRole(value) { role.value = value; section.value = 'home'; selectedCustomer.value = null }
function setSection(value) { section.value = value; if (value !== 'customer-detail') selectedCustomer.value = null }
function exit() { uni.reLaunch({ url: '/pages/entry/index' }) }
function submitDemo() {
  if (!form.name.trim()) return toast('请填写客户名称')
  if (!/^1\d{10}$/.test(form.phone.trim())) return toast('请输入正确的演示手机号')
  partnerCustomers.value.unshift({ id: Date.now(), name: form.name.trim(), contact: form.contact.trim() || '待补充', phone: form.phone.slice(0, 3) + '****' + form.phone.slice(-4), service: '一件代发', status: '待跟进', statusTone: 'gold', stage: '待首次沟通', lock: '演示预锁', updated: '刚刚新增' })
  form.name = ''; form.contact = ''; form.phone = ''; showAdd.value = false; toast('已加入演示客户，仅当前页面有效')
}
function demoOnly() { toast('当前为体验模式，不会提交数据；登录后可使用') }
function toast(message) { notice.value = message; setTimeout(() => { notice.value = '' }, 2200) }
</script>

<style scoped>
.demo-page { padding: 26rpx 0 70rpx; background: #f5f7fb; overflow-wrap: anywhere; }
.demo-topbar { display: flex; align-items: flex-start; justify-content: space-between; margin: 0 26rpx 22rpx; }
.demo-kicker, .demo-eyebrow, .demo-section-label { color: #7180a1; font-size: 19rpx; letter-spacing: 3rpx; font-weight: 700; }
.demo-mode { display: flex; align-items: center; gap: 8rpx; margin-top: 7rpx; color: #20305d; font-size: 24rpx; font-weight: 650; }
.demo-live-dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: #39c49a; box-shadow: 0 0 0 6rpx rgba(57,196,154,.13); }
.demo-exit { min-width: 102rpx; margin: 0; padding: 10rpx 18rpx; color: #596581; border: 1rpx solid #dfe4f0; border-radius: 999rpx; background: #fff; font-size: 23rpx; line-height: 1.3; }
.demo-hero { position: relative; margin: 0 24rpx; padding: 34rpx 30rpx 32rpx; overflow: hidden; color: #fff; border-radius: 28rpx; background: linear-gradient(135deg, #101a3d 0%, #1d2d63 55%, #3857f5 100%); box-shadow: 0 22rpx 44rpx rgba(29,42,87,.18); }
.demo-hero-glow { position: absolute; right: -110rpx; bottom: -150rpx; width: 390rpx; height: 390rpx; border: 1rpx solid rgba(255,255,255,.2); border-radius: 50%; box-shadow: 0 0 0 30rpx rgba(255,255,255,.04), 0 0 0 60rpx rgba(255,255,255,.03); }
.demo-hero > view:not(.demo-hero-glow) { position: relative; z-index: 1; }
.demo-hero .demo-eyebrow { color: #7de1e7; }
.demo-title { margin-top: 18rpx; font-size: 50rpx; font-weight: 750; letter-spacing: -1rpx; }
.demo-subtitle { max-width: 560rpx; margin-top: 12rpx; color: rgba(255,255,255,.72); font-size: 25rpx; line-height: 1.6; }
.demo-safe-note { display: inline-flex; margin-top: 24rpx; padding: 9rpx 14rpx; color: #d8f8f4; border: 1rpx solid rgba(125,225,231,.26); border-radius: 999rpx; background: rgba(125,225,231,.12); font-size: 21rpx; }
.role-switch { display: flex; gap: 10rpx; margin: 24rpx 24rpx 10rpx; padding: 8rpx; border-radius: 18rpx; background: #e8ecf5; }
.role-switch-item { flex: 1; min-height: 72rpx; margin: 0; color: #78839d; border-radius: 13rpx; background: transparent; font-size: 26rpx; line-height: 72rpx; }
.role-switch-item.active { color: #1a2853; background: #fff; box-shadow: 0 7rpx 18rpx rgba(34,49,95,.09); font-weight: 700; }
.demo-nav { width: 100%; margin: 14rpx 0 6rpx; }
.demo-nav-content { display: inline-block; min-width: 100%; padding: 0 24rpx 10rpx; white-space: nowrap; }
.demo-nav-item { display: inline-flex; align-items: center; gap: 7rpx; margin-right: 26rpx; padding: 12rpx 0; color: #8a94aa; border-bottom: 4rpx solid transparent; font-size: 24rpx; }
.demo-nav-item.active { color: #3857f5; border-bottom-color: #3857f5; font-weight: 700; }
.demo-nav-icon { font-size: 25rpx; }
.demo-section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 16rpx; margin: 24rpx 26rpx 16rpx; }
.demo-section-heading.compact { align-items: center; margin-top: 34rpx; }
.demo-section-title { margin-top: 5rpx; color: #17203e; font-size: 34rpx; font-weight: 750; letter-spacing: -.5rpx; }
.demo-section-link, .demo-date { color: #8c96aa; font-size: 22rpx; }
.demo-metric-hero, .warehouse-demo-hero, .warehouse-settle-hero { margin: 0 24rpx 18rpx; padding: 28rpx; color: #fff; border-radius: 24rpx; background: linear-gradient(135deg, #111936, #293e89); box-shadow: 0 16rpx 32rpx rgba(29,42,87,.13); }
.metric-caption { color: rgba(255,255,255,.66); font-size: 23rpx; }
.metric-number { margin: 10rpx 0 16rpx; font-size: 61rpx; font-weight: 750; letter-spacing: -2rpx; }
.metric-foot { display: flex; justify-content: space-between; color: rgba(255,255,255,.72); font-size: 22rpx; }
.demo-stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12rpx; margin: 0 24rpx; }
.demo-stat { min-height: 156rpx; padding: 18rpx 12rpx; border: 1rpx solid #e7ebf4; border-radius: 18rpx; background: #fff; box-shadow: 0 7rpx 20rpx rgba(29,42,87,.04); }
.stat-icon, .action-icon { display: flex; align-items: center; justify-content: center; color: #fff; border-radius: 14rpx; font-size: 21rpx; font-weight: 700; }
.stat-icon { width: 48rpx; height: 48rpx; }
.stat-icon.blue, .action-icon.blue { background: linear-gradient(145deg, #6f86ff, #3857f5); }
.stat-icon.cyan, .action-icon.cyan { background: linear-gradient(145deg, #69dce1, #299cb0); }
.stat-icon.gold, .action-icon.gold { background: linear-gradient(145deg, #ffd77d, #df961c); }
.stat-icon.purple, .action-icon.purple { background: linear-gradient(145deg, #a993ff, #6953cf); }
.stat-value { margin-top: 14rpx; color: #17203e; font-size: 30rpx; font-weight: 750; }
.stat-label { margin-top: 4rpx; color: #8a94aa; font-size: 20rpx; }
.demo-action-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14rpx; margin: 0 24rpx; }
.demo-action { position: relative; min-height: 156rpx; padding: 20rpx; border: 1rpx solid #e7ebf4; border-radius: 20rpx; background: #fff; box-shadow: 0 7rpx 20rpx rgba(29,42,87,.04); }
.action-icon { width: 52rpx; height: 52rpx; }
.action-name { margin-top: 15rpx; color: #17203e; font-size: 27rpx; font-weight: 700; }
.action-desc { margin-top: 4rpx; color: #8a94aa; font-size: 21rpx; }
.action-arrow { position: absolute; right: 18rpx; top: 22rpx; color: #b7c0d1; font-size: 32rpx; }
.demo-card { margin: 16rpx 24rpx; padding: 24rpx; border: 1rpx solid #e7ebf4; border-radius: 22rpx; background: #fff; box-shadow: 0 8rpx 22rpx rgba(29,42,87,.04); }
.card-title-row, .customer-head, .customer-meta, .detail-profile, .detail-status, .order-info, .demo-team-hero, .erp-row { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.demo-card-title { color: #17203e; font-size: 28rpx; font-weight: 700; }
.demo-muted, .activity-time { color: #8993a8; font-size: 21rpx; line-height: 1.5; }
.activity-row { display: flex; align-items: center; gap: 16rpx; padding: 20rpx 0; border-bottom: 1rpx solid #edf0f6; }
.activity-row:last-child, .income-row:last-child, .team-row:last-child, .notice-row:last-child, .erp-row:last-child { border-bottom: 0; }
.activity-mark { display: flex; align-items: center; justify-content: center; width: 54rpx; height: 54rpx; flex: 0 0 54rpx; color: #3857f5; border-radius: 17rpx; background: #eef1ff; font-size: 22rpx; font-weight: 700; }
.warehouse-mark { color: #16869a; background: #eaf9fa; }
.activity-main, .customer-main, .team-main, .erp-main { flex: 1; min-width: 0; }
.activity-title { color: #253052; font-size: 25rpx; font-weight: 600; }
.activity-time { margin-top: 5rpx; }
.activity-amount { color: #3a58d7; font-size: 24rpx; font-weight: 700; white-space: nowrap; }
.status-tag { display: inline-flex; padding: 7rpx 12rpx; color: #3857f5; border-radius: 999rpx; background: #eef1ff; font-size: 20rpx; white-space: nowrap; }
.status-tag.ok { color: #14876e; background: #eaf8f3; }.status-tag.gold { color: #a46d08; background: #fff5df; }
.status-tag.blue { color: #3857f5; background: #eef1ff; }
.customer-tip { display: flex; align-items: center; gap: 14rpx; background: #f4f6ff; }
.tip-mark { display: flex; align-items: center; justify-content: center; width: 42rpx; height: 42rpx; color: #3857f5; border-radius: 50%; background: #dfe5ff; font-size: 24rpx; font-weight: 700; }
.tip-title { color: #273561; font-size: 25rpx; font-weight: 650; }
.customer-card { margin-top: 14rpx; }.customer-avatar, .detail-avatar, .team-avatar { display: flex; align-items: center; justify-content: center; flex: 0 0 68rpx; width: 68rpx; height: 68rpx; color: #3857f5; border-radius: 22rpx; background: #e8edff; font-size: 30rpx; font-weight: 750; }
.customer-main { margin-right: auto; }.customer-name { color: #17203e; font-size: 29rpx; font-weight: 700; }.customer-meta { margin-top: 20rpx; padding-top: 18rpx; color: #8a94aa; border-top: 1rpx solid #edf0f6; font-size: 21rpx; }.customer-meta text { margin-right: 16rpx; }.customer-arrow { margin-left: auto; color: #3857f5 !important; }
.mini-action { min-width: 126rpx; margin: 0; padding: 10rpx 18rpx; color: #3857f5; border: 1rpx solid #dbe2ff; border-radius: 999rpx; background: #f3f5ff; font-size: 22rpx; line-height: 1.3; }
.empty-card { color: #8993a8; text-align: center; font-size: 24rpx; }
.detail-card { padding: 28rpx; }.detail-profile { justify-content: flex-start; }.detail-name { color: #17203e; font-size: 34rpx; font-weight: 750; }.detail-status { margin: 24rpx 0 10rpx; }.detail-line { display: flex; justify-content: space-between; padding: 21rpx 0; color: #8993a8; border-bottom: 1rpx solid #edf0f6; font-size: 24rpx; }.detail-line text:last-child { color: #253052; }.demo-disabled-button { width: 100%; margin: 22rpx 0 0; padding: 18rpx; color: #77829b; border: 1rpx dashed #cbd3e4; border-radius: 15rpx; background: #f8f9fc; font-size: 24rpx; line-height: 1.4; }
.demo-income-hero { display: flex; align-items: flex-end; justify-content: space-between; margin: 0 24rpx 16rpx; padding: 27rpx; border-radius: 22rpx; background: #eaf8f3; }.demo-income-hero .metric-caption { color: #5e8d82; }.income-number { margin-top: 8rpx; color: #147a62; font-size: 46rpx; font-weight: 750; }.income-trend { color: #147a62; font-size: 25rpx; font-weight: 700; }.income-row { display: flex; justify-content: space-between; gap: 16rpx; padding: 21rpx 0; border-bottom: 1rpx solid #edf0f6; }.income-value { color: #147a62; font-size: 25rpx; font-weight: 700; white-space: nowrap; }
.demo-team-hero { justify-content: flex-start; margin: 0 24rpx 16rpx; padding: 24rpx; border-radius: 22rpx; background: #f1efff; }.team-ring { display: flex; align-items: center; justify-content: center; width: 86rpx; height: 86rpx; margin-right: 6rpx; color: #6953cf; border: 6rpx solid #9b8bf4; border-radius: 50%; font-size: 23rpx; font-weight: 750; }.team-title { color: #3d327f; font-size: 28rpx; font-weight: 700; }.team-row { display: flex; align-items: center; gap: 14rpx; padding: 19rpx 0; border-bottom: 1rpx solid #edf0f6; }.team-avatar { width: 56rpx; height: 56rpx; flex-basis: 56rpx; border-radius: 18rpx; color: #6953cf; background: #f0edff; font-size: 24rpx; }.team-score { color: #6953cf; font-size: 23rpx; font-weight: 700; }
.notice-row { display: flex; gap: 16rpx; padding: 21rpx 0; border-bottom: 1rpx solid #edf0f6; }.notice-dot { width: 16rpx; height: 16rpx; flex: 0 0 16rpx; margin-top: 8rpx; border-radius: 50%; background: #3857f5; }.notice-dot.gold { background: #f5b84b; }.notice-dot.cyan { background: #42bdc9; }.notice-copy { margin-top: 9rpx; color: #5d6881; font-size: 22rpx; line-height: 1.5; }
.warehouse-status { color: #aaf0d7; font-size: 22rpx; }.warehouse-demo-hero { background: linear-gradient(135deg, #0f3b48, #16798b); }.warehouse-demo-title { margin-top: 13rpx; font-size: 40rpx; font-weight: 750; }.warehouse-demo-subtitle { margin-top: 6rpx; color: rgba(255,255,255,.7); font-size: 23rpx; }.warehouse-progress { margin-top: 28rpx; }.progress-fill { width: 72%; height: 10rpx; border-radius: 999rpx; background: #7de1d1; }.progress-label { display: flex; justify-content: space-between; margin-top: 8rpx; color: rgba(255,255,255,.7); font-size: 20rpx; }.order-card { padding: 24rpx; }.order-id { color: #253052; font-size: 25rpx; font-weight: 700; }.order-info { align-items: flex-start; margin-top: 22rpx; padding-top: 20rpx; border-top: 1rpx solid #edf0f6; }.order-info > view { flex: 1; }.order-info > view:not(:last-child) { border-right: 1rpx solid #edf0f6; }.order-info > view + view { padding-left: 16rpx; }.erp-row { justify-content: flex-start; padding: 22rpx 0; border-bottom: 1rpx solid #edf0f6; }.erp-icon { display: flex; align-items: center; justify-content: center; width: 58rpx; height: 58rpx; color: #16869a; border-radius: 17rpx; background: #eaf9fa; font-size: 22rpx; font-weight: 700; }.erp-main { margin-right: auto; }.timeline-row { display: flex; align-items: flex-start; gap: 16rpx; position: relative; padding: 22rpx 0; }.timeline-row:not(:last-child)::after { position: absolute; top: 55rpx; left: 19rpx; width: 2rpx; height: calc(100% - 28rpx); background: #e1e6f0; content: ''; }.timeline-dot { display: flex; align-items: center; justify-content: center; width: 40rpx; height: 40rpx; flex: 0 0 40rpx; z-index: 1; color: #8993a8; border: 2rpx solid #d5dce9; border-radius: 50%; background: #fff; font-size: 20rpx; }.timeline-dot.done { color: #fff; border-color: #39b894; background: #39b894; }.timeline-main { flex: 1; }.timeline-status { color: #8993a8; font-size: 21rpx; }.done-text { color: #14876e; }.warehouse-settle-hero { background: linear-gradient(135deg, #633f15, #b9771a); }
.demo-boundary { display: flex; gap: 14rpx; margin: 28rpx 24rpx 0; padding: 20rpx; border: 1rpx solid #e7eaf2; border-radius: 18rpx; background: #fff; }.boundary-icon { display: flex; align-items: center; justify-content: center; width: 40rpx; height: 40rpx; flex: 0 0 40rpx; color: #687592; border-radius: 50%; background: #eef1f7; }.boundary-title { color: #415071; font-size: 23rpx; font-weight: 700; }.boundary-copy { margin-top: 5rpx; color: #8b95aa; font-size: 21rpx; line-height: 1.5; }
.demo-sheet-mask { position: fixed; inset: 0; z-index: 10; display: flex; align-items: flex-end; background: rgba(17,25,54,.36); }.demo-sheet { width: 100%; padding: 20rpx 28rpx 42rpx; border-radius: 30rpx 30rpx 0 0; background: #fff; box-shadow: 0 -12rpx 36rpx rgba(17,25,54,.18); }.sheet-handle { width: 72rpx; height: 8rpx; margin: 0 auto 24rpx; border-radius: 999rpx; background: #dfe4ee; }.sheet-note { margin: 8rpx 0 16rpx; }.sheet-cancel { width: 100%; margin-top: 12rpx; padding: 18rpx; color: #687592; background: transparent; font-size: 26rpx; line-height: 1.4; }.demo-toast { position: fixed; left: 50%; bottom: 56rpx; z-index: 20; width: calc(100% - 48rpx); max-width: 620rpx; padding: 17rpx 24rpx; color: #fff; border-radius: 999rpx; background: rgba(17,25,54,.9); font-size: 23rpx; transform: translateX(-50%); white-space: normal; text-align: center; overflow-wrap: anywhere; }
</style>
