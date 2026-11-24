<template>
  <section class="tariff">
    <h3>标准报价明细</h3>
    <el-select v-model="sectionKey" aria-label="报价附表" @change="search=''">
      <el-option v-for="s in sections" :key="s.key" :label="s.label" :value="s.key" />
    </el-select>
    <el-input v-model="search" clearable placeholder="查找省份、规格或收费项目" />
    <p v-if="sectionKey==='stage'">一口价已含快递、操作和包材。填写一口价时优先采用该价，再扣除特殊减免。</p>
    <el-table v-if="section" :data="visibleRows" height="420" border row-key="id">
      <el-table-column prop="label" label="地区／项目" fixed width="195" />
      <el-table-column v-for="col in section.columns" :key="col.key" :label="col.label" :min-width="col.kind==='text'?390:140">
        <template #default="{row}">
          <el-input :type="col.kind==='text'?'textarea':'text'" :maxlength="col.kind==='text'?2000:13"
            :model-value="cells[row.id]?.[col.key]" :disabled="!editable"
            :placeholder="col.kind==='text'?'填写条款':'待询价'" @update:model-value="setCell(row.id,col.key,$event)" />
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
<script setup>
import {computed,ref} from 'vue'
const props=defineProps({ sections:{type:Array,required:true}, cells:{type:Object,required:true}, editable:Boolean })
const sectionKey=ref(props.sections[0]?.key)
const search=ref('')
const section=computed(()=>props.sections.find(s=>s.key===sectionKey.value))
const visibleRows=computed(()=>section.value?.rows.filter(r=>r.label.includes(search.value.trim())) || [])
function setCell(id,key,value) {
  const row=props.cells[id] || (props.cells[id]={})
  row[key]=value
}
</script>
<style scoped>
.tariff{margin:24px 0}.tariff>.el-select,.tariff>.el-input{width:min(100%,280px);margin:0 12px 12px 0}
.tariff p{font-size:13px;color:var(--el-text-color-secondary)}
</style>
