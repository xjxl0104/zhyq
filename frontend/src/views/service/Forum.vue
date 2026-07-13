<template>
  <div class="page-container">
    <!-- 统计卡:点击切换筛选 -->
    <div class="stat-row">
      <div class="stat-card" :class="{ active: query.status === null }" @click="filterByStatus(null)">
        <div class="stat-label">全部</div>
        <div class="num">{{ stats.total }}</div>
      </div>
      <div class="stat-card warn" :class="{ active: query.status === 1 }" @click="filterByStatus(1)">
        <div class="stat-label">待审核</div>
        <div class="num">{{ stats.pending }}</div>
      </div>
      <div class="stat-card ok" :class="{ active: query.status === 2 }" @click="filterByStatus(2)">
        <div class="stat-label">已发布</div>
        <div class="num">{{ stats.published }}</div>
      </div>
      <div class="stat-card gray" :class="{ active: query.status === 3 }" @click="filterByStatus(3)">
        <div class="stat-label">已下架</div>
        <div class="num">{{ stats.offline }}</div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar-bar">
      <el-button type="primary" @click="openPost"><el-icon><EditPen /></el-icon>发帖</el-button>
      <div class="filters">
        <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 130px" @change="load">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-input v-model="query.title" placeholder="搜索标题" clearable style="width: 200px"
                  @keyup.enter="load" @clear="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="load">查询</el-button>
      </div>
    </div>

    <!-- 帖子卡片流 -->
    <div class="feed" v-loading="loading">
      <div v-for="row in list" :key="row.id" class="post-card" :class="{ pending: row.status === 1 }">
        <div class="post-head">
          <el-tag size="small" :type="categoryTag(row.category)" effect="light">{{ row.category }}</el-tag>
          <el-tag size="small" :type="statusMeta(row.status).type" effect="plain">{{ statusMeta(row.status).text }}</el-tag>
          <span class="post-title" @click="openDetail(row)">{{ row.title }}</span>
        </div>
        <div class="post-body">{{ row.content }}</div>
        <div class="post-foot">
          <div class="meta">
            <span class="author"><el-icon><User /></el-icon>{{ row.author || '匿名' }}</span>
            <span class="time">{{ row.createTime }}</span>
            <span class="count"><el-icon><ChatDotRound /></el-icon>{{ row.replyCount }}</span>
            <span class="count"><el-icon><Pointer /></el-icon>{{ row.likeCount }}</span>
          </div>
          <div class="ops">
            <template v-if="row.status === 1">
              <el-button link type="success" @click="approve(row)">通过</el-button>
              <el-popconfirm title="确认删除该帖?" @confirm="remove(row.id)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
            <template v-else-if="row.status === 2">
              <el-button link type="primary" @click="openDetail(row)">查看</el-button>
              <el-popconfirm title="确认下架该帖?" @confirm="offline(row.id)">
                <template #reference><el-button link type="warning">下架</el-button></template>
              </el-popconfirm>
            </template>
            <template v-else>
              <el-button link type="primary" @click="openDetail(row)">查看</el-button>
              <el-popconfirm title="确认删除该帖?" @confirm="remove(row.id)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </div>
        </div>
      </div>
      <el-empty v-if="!loading && list.length === 0" description="暂无帖子" />
    </div>

    <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                   :total="total" v-model:current-page="query.pageNo"
                   v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />

    <!-- 发帖弹窗 -->
    <el-dialog v-model="postDialog.visible" title="发帖" width="560px">
      <el-form :model="postForm" label-width="70px" ref="postRef" :rules="postRules">
        <el-form-item label="分类" prop="category">
          <el-select v-model="postForm.category" placeholder="请选择分类" style="width: 180px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="postForm.title" placeholder="请输入标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="作者" prop="author">
          <el-input v-model="postForm.author" placeholder="请输入昵称" style="width: 220px" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="postForm.content" type="textarea" :rows="5" placeholder="请输入内容"
                    maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="postDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitPost">发布(需审核)</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawer.visible" :title="detail.post?.title || '帖子详情'" size="520px">
      <div v-if="detail.post" class="detail">
        <div class="detail-head">
          <el-tag size="small" :type="categoryTag(detail.post.category)" effect="light">{{ detail.post.category }}</el-tag>
          <span class="d-author">{{ detail.post.author || '匿名' }}</span>
          <span class="d-time">{{ detail.post.createTime }}</span>
        </div>
        <div class="detail-content">{{ detail.post.content }}</div>
        <div class="detail-actions">
          <el-button size="small" @click="doLike"><el-icon><Pointer /></el-icon>点赞 {{ detail.post.likeCount }}</el-button>
          <span class="reply-total">共 {{ detail.replies.length }} 条回复</span>
        </div>

        <el-timeline v-if="detail.replies.length" class="reply-line">
          <el-timeline-item v-for="r in detail.replies" :key="r.id" :timestamp="r.createTime" placement="top">
            <div class="reply-item">
              <span class="r-author">{{ r.author || '匿名' }}</span>
              <div class="r-content">{{ r.content }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无回复" :image-size="60" />

        <div class="reply-box" v-if="detail.post.status === 2">
          <el-input v-model="replyForm.content" type="textarea" :rows="2" placeholder="写下你的回复…" maxlength="500" />
          <div class="reply-box-foot">
            <el-input v-model="replyForm.author" placeholder="昵称" size="small" style="width: 140px" />
            <el-button type="primary" size="small" @click="submitReply">回复</el-button>
          </div>
        </div>
        <el-alert v-else type="info" :closable="false" show-icon
                  title="该帖非已发布状态,暂不可回复" style="margin-top: 12px" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { forumApi } from '@/api/service'

const categories = ['闲聊', '求助', '二手', '活动']

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, category: '', status: null, title: '' })
const stats = reactive({ total: 0, pending: 0, published: 0, offline: 0 })

function statusMeta(s) {
  if (s === 1) return { text: '待审核', type: 'warning' }
  if (s === 2) return { text: '已发布', type: 'success' }
  return { text: '已下架', type: 'info' }
}
function categoryTag(c) {
  return { 求助: 'danger', 二手: 'warning', 活动: 'success' }[c] || 'primary'
}

async function load() {
  loading.value = true
  try {
    const res = await forumApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
async function loadStats() {
  Object.assign(stats, await forumApi.stats())
}
function filterByStatus(s) {
  query.status = s
  query.pageNo = 1
  load()
}

// 发帖
const postRef = ref()
const postDialog = reactive({ visible: false })
const postForm = reactive({ category: '闲聊', title: '', author: '', content: '' })
const postRules = {
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}
function openPost() {
  postDialog.visible = true
  Object.assign(postForm, { category: '闲聊', title: '', author: '', content: '' })
}
async function submitPost() {
  await postRef.value.validate()
  // 敏感词命中时后端抛 BizException,request 拦截器已自动 ElMessage 提示
  await forumApi.add(postForm)
  ElMessage.success('发布成功,等待审核')
  postDialog.visible = false
  load()
  loadStats()
}

async function approve(row) {
  await forumApi.approve(row.id)
  ElMessage.success('审核通过')
  load()
  loadStats()
}
async function offline(id) {
  await forumApi.offline(id)
  ElMessage.success('已下架')
  load()
  loadStats()
}
async function remove(id) {
  await forumApi.remove(id)
  ElMessage.success('删除成功')
  load()
  loadStats()
}

// 详情抽屉
const drawer = reactive({ visible: false })
const detail = reactive({ post: null, replies: [] })
const replyForm = reactive({ content: '', author: '' })

async function openDetail(row) {
  const res = await forumApi.get(row.id)
  detail.post = res.post
  detail.replies = res.replies || []
  Object.assign(replyForm, { content: '', author: '' })
  drawer.visible = true
}
async function submitReply() {
  if (!replyForm.content.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  await forumApi.reply(detail.post.id, { content: replyForm.content, author: replyForm.author })
  ElMessage.success('回复成功')
  replyForm.content = ''
  await openDetail(detail.post)
  load()
}
async function doLike() {
  await forumApi.like(detail.post.id)
  detail.post.likeCount = (detail.post.likeCount || 0) + 1
  load()
}

onMounted(() => {
  load()
  loadStats()
})
</script>

<style scoped>
/* 统计卡 */
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
.stat-card {
  background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius);
  padding: 16px 18px; cursor: pointer; transition: all .15s;
}
.stat-card:hover { border-color: var(--border-strong); transform: translateY(-2px); box-shadow: var(--shadow-card); }
.stat-card.active { border-color: var(--brand); box-shadow: 0 0 0 1px var(--brand); }
.stat-card .stat-label { color: var(--text-secondary); font-size: 13px; margin-bottom: 6px; }
.stat-card .num { font-size: 26px; font-weight: 700; color: var(--text-title); }
.stat-card.warn .num { color: var(--el-color-warning); }
.stat-card.ok .num { color: var(--el-color-success); }
.stat-card.gray .num { color: var(--text-muted); }

/* 工具栏 */
.toolbar-bar {
  display: flex; justify-content: space-between; align-items: center;
  background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius);
  padding: 12px 16px; margin-bottom: 16px;
}
.toolbar-bar .filters { display: flex; gap: 10px; align-items: center; }

/* 帖子卡片流 */
.feed { display: flex; flex-direction: column; gap: 12px; min-height: 120px; }
.post-card {
  background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius);
  padding: 16px 18px; transition: all .15s; border-left: 3px solid transparent;
}
.post-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-card); border-color: var(--border-strong); }
.post-card.pending { border-left-color: var(--el-color-warning); }

.post-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.post-title {
  font-size: 16px; font-weight: 600; color: var(--text-title); cursor: pointer; margin-left: 2px;
}
.post-title:hover { color: var(--brand); }

.post-body {
  color: var(--text-secondary); font-size: 14px; line-height: 1.6;
  display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden; margin-bottom: 12px;
}
.post-foot { display: flex; justify-content: space-between; align-items: center; }
.post-foot .meta { display: flex; align-items: center; gap: 16px; color: var(--text-muted); font-size: 13px; }
.post-foot .meta .el-icon { vertical-align: -2px; margin-right: 3px; }
.post-foot .meta .author { color: var(--text-secondary); }

.pager { margin-top: 16px; justify-content: flex-end; }

/* 详情抽屉 */
.detail-head { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.detail-head .d-author { color: var(--text-body); font-weight: 500; }
.detail-head .d-time { color: var(--text-muted); font-size: 13px; }
.detail-content {
  color: var(--text-body); font-size: 14px; line-height: 1.7; white-space: pre-wrap;
  padding: 14px; background: var(--bg-subtle); border-radius: var(--radius-sm); margin-bottom: 14px;
}
.detail-actions {
  display: flex; align-items: center; justify-content: space-between;
  padding-bottom: 12px; border-bottom: 1px solid var(--border); margin-bottom: 12px;
}
.detail-actions .reply-total { color: var(--text-secondary); font-size: 13px; }
.reply-item .r-author { font-weight: 600; color: var(--text-title); font-size: 13px; }
.reply-item .r-content { color: var(--text-body); font-size: 14px; margin-top: 4px; line-height: 1.6; }

.reply-box { margin-top: 16px; border-top: 1px dashed var(--border); padding-top: 14px; }
.reply-box-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; }
</style>
