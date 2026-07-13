<template>
  <div class="page-container">
    <!-- 我的常用 -->
    <div class="fav-card">
      <div class="section-title">
        <el-icon class="title-icon"><StarFilled /></el-icon>
        <span>我的常用</span>
      </div>
      <div v-if="favorites.length" class="fav-grid">
        <div v-for="app in favorites" :key="app.id" class="fav-item" @click="openApp(app)">
          <span class="fav-remove" title="取消收藏" @click.stop="removeFavorite(app)">
            <el-icon><Close /></el-icon>
          </span>
          <div class="fav-icon">
            <el-icon :size="26"><component :is="app.icon || 'Menu'" /></el-icon>
          </div>
          <div class="fav-name">{{ app.name }}</div>
        </div>
      </div>
      <el-empty v-else description="暂无常用应用,点击下方应用卡片右上角星标添加" :image-size="60" />
    </div>

    <!-- 全部应用 -->
    <div class="app-card">
      <div class="app-header">
        <div class="section-title">
          <el-icon class="title-icon"><Grid /></el-icon>
          <span>全部应用</span>
        </div>
        <el-input v-model="keyword" placeholder="搜索应用名称" clearable style="width: 240px"
                  @input="loadApps">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <el-tabs v-model="activeCategory" @tab-change="loadApps">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane v-for="c in categories" :key="c" :label="c" :name="c" />
      </el-tabs>

      <div v-loading="loading" class="app-grid">
        <div v-for="app in apps" :key="app.id" class="app-item" @click="openApp(app)">
          <span class="app-star" :class="{ active: isFavorite(app.id) }"
                :title="isFavorite(app.id) ? '取消常用' : '添加常用'"
                @click.stop="toggleFavorite(app)">
            <el-icon><StarFilled v-if="isFavorite(app.id)" /><Star v-else /></el-icon>
          </span>
          <div class="app-icon">
            <el-icon :size="30"><component :is="app.icon || 'Menu'" /></el-icon>
          </div>
          <div class="app-info">
            <div class="app-name">{{ app.name }}</div>
            <div class="app-desc">{{ app.description || '—' }}</div>
            <el-tag size="small" effect="plain" class="app-cat">{{ app.category }}</el-tag>
          </div>
        </div>
        <el-empty v-if="!loading && !apps.length" description="没有找到匹配的应用" class="grid-empty" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { appApi } from '@/api/app'

const router = useRouter()
const loading = ref(false)
const apps = ref([])
const favorites = ref([])
const categories = ref([])
const activeCategory = ref('')
const keyword = ref('')

const favoriteIds = computed(() => new Set(favorites.value.map(a => a.id)))
const isFavorite = (id) => favoriteIds.value.has(id)

async function loadApps() {
  loading.value = true
  try {
    apps.value = await appApi.list({
      category: activeCategory.value || undefined,
      name: keyword.value || undefined
    })
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  categories.value = await appApi.categories()
}

async function loadFavorites() {
  favorites.value = await appApi.favoriteList()
}

function openApp(app) {
  if (app.path) router.push(app.path)
  else ElMessage.warning('该应用暂未配置跳转路由')
}

async function toggleFavorite(app) {
  if (isFavorite(app.id)) {
    await appApi.removeFavorite(app.id)
    ElMessage.success('已取消常用')
  } else {
    await appApi.addFavorite(app.id)
    ElMessage.success('已添加到常用')
  }
  loadFavorites()
}

async function removeFavorite(app) {
  await appApi.removeFavorite(app.id)
  ElMessage.success('已取消常用')
  loadFavorites()
}

onMounted(() => {
  loadFavorites()
  loadCategories()
  loadApps()
})
</script>

<style scoped>
.fav-card, .app-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 14px;
}
.title-icon { color: #f7ba2a; }
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.app-header .section-title { margin-bottom: 0; }

/* 我的常用 */
.fav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 12px;
}
.fav-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 8px 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.fav-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}
.fav-icon {
  width: 46px;
  height: 46px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409eff;
  background: #ecf5ff;
  margin-bottom: 8px;
}
.fav-name { font-size: 13px; color: #303133; }
.fav-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  display: none;
  color: #909399;
  font-size: 12px;
  line-height: 1;
  padding: 2px;
}
.fav-item:hover .fav-remove { display: inline-flex; }
.fav-remove:hover { color: #f56c6c; }

/* 应用网格 */
.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 14px;
  min-height: 120px;
}
.app-item {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 16px 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.app-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}
.app-icon {
  flex-shrink: 0;
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409eff;
  background: linear-gradient(135deg, #ecf5ff, #d9ecff);
}
.app-info { flex: 1; min-width: 0; }
.app-name { font-size: 14px; font-weight: 600; color: #303133; }
.app-desc {
  font-size: 12px;
  color: #909399;
  margin: 4px 0 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.app-star {
  position: absolute;
  top: 8px;
  right: 10px;
  color: #c0c4cc;
  font-size: 16px;
  line-height: 1;
}
.app-star:hover { color: #f7ba2a; }
.app-star.active { color: #f7ba2a; }
.grid-empty { grid-column: 1 / -1; }
</style>
