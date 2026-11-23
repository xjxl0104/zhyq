<template><view class="page-wrap"><view class="card"><view class="wh-title">承接客户</view><view class="wh-note">主账号查看本仓客户；业务员仅查看分配给自己的客户。点击客户查看详情、分配业务员及记录服务进度。</view><view v-if="loading" class="wh-note">正在读取客户…</view><view v-if="error" class="wh-error">{{ error }}</view><view v-if="!loading && !rows.length && !error" class="wh-note">暂无分派客户，园区分派后会出现在这里。</view><view v-for="c in rows" :key="c.id" class="wh-item"><view class="wh-item-title" @click="openCustomer(c)">{{ c.name }} ›</view><view class="wh-status">{{ assignmentLabel(c.warehouseAssignmentStatus) }} · {{ STATUS[c.status] || '状态待确认' }}</view><view class="wh-meta">{{ c.contact || '' }} {{ c.phone || '' }}</view><view class="wh-meta">业务员：{{ c.salespersonName || '待分配' }}</view><view class="wh-note">{{ c.publicProgress || '暂无进度更新' }}</view><button class="wh-secondary" @click="openCustomer(c)">查看客户详情与进度</button></view></view><view class="wh-actions wh-pager"><button class="wh-secondary" :disabled="page===1||loading" @click="load(page-1)">上一页</button><view class="wh-note">第 {{ page }} 页 · 共 {{ total }} 条</view><button class="wh-secondary" :disabled="page*20>=total||loading" @click="load(page+1)">下一页</button></view><button v-if="error" class="wh-secondary wh-pager" @click="load(page)">重试</button></view></template>
<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import {ref} from 'vue'; import {onShow} from '@dcloudio/uni-app'; import {warehouseApi} from '@/api/warehouse'; import {assignmentLabel} from '@/utils/warehouse-ui'
import CustomerRequirements from '@/components/CustomerRequirements.vue'
import CustomerProgress from '@/components/CustomerProgress.vue'
const detailId = ref(null), progressPercent = ref(0)
function openCustomer(c) { uni.navigateTo({ url: '/pages/warehouse-customer-detail/index?id=' + c.id }) }
const STATUS={1:'跟进中',2:'已签约',3:'已流失'}
const rows=ref([]),page=ref(1),total=ref(0),loading=ref(false),busy=ref(false),error=ref(''),editingId=ref(null),editMode=ref(''),summary=ref('')
async function load(next=page.value){loading.value=true;error.value='';try{const r=await warehouseApi.customers({pageNo:next,pageSize:20});rows.value=r.records||[];total.value=r.total||0;page.value=next}catch(e){rows.value=[];error.value=e.message||'客户读取失败，请重试'}finally{loading.value=false}}
onShow(()=>load())
</script><style scoped>@import '../../styles/warehouse.css';</style>
