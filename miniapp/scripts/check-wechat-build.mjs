import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
const manifest = JSON.parse(readFileSync(resolve('src/manifest.json'), 'utf8'))
const project = JSON.parse(readFileSync(resolve('dist/build/mp-weixin/project.config.json'), 'utf8'))
if (project.appid !== manifest['mp-weixin'].appid) {
  throw new Error('构建产物 AppID 与源码不一致，请清理旧产物后重新构建。')
}
console.log(`微信构建已核验 AppID：${project.appid}。请在开发者工具重新编译此目录后再真机预览。`)
