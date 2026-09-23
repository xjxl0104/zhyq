import test from 'node:test'
import assert from 'node:assert/strict'
import {readdirSync,readFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {join} from 'node:path'
const src=fileURLToPath(new URL('../src/',import.meta.url))
const tabs=new Set(JSON.parse(readFileSync(join(src,'pages.json'),'utf8')).tabBar.list.map(x=>'/'+x.pagePath))
const vueFiles=dir=>readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?vueFiles(join(dir,e.name)):e.name.endsWith('.vue')?[join(dir,e.name)]:[])
test('真机 tabBar 页面不能使用 navigateTo 或 redirectTo',()=>{
  for(const file of vueFiles(join(src,'pages'))){
    const text=readFileSync(file,'utf8')
    for(const match of text.matchAll(/uni\.(navigateTo|redirectTo)\(\{\s*url:\s*['"]([^'"]+)['"]/g))assert.ok(!tabs.has(match[2].split('?')[0]),`${file} ${match[1]} cannot open ${match[2]}`)
  }
  for(const page of ['team','withdraw'])assert.match(readFileSync(join(src,'pages',page,'index.vue'),'utf8'),/uni\.switchTab\(\{\s*url:\s*['"]\/pages\/me\/index['"]/)
})
