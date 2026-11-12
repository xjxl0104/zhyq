import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { parse, compileScript } from 'vue/compiler-sfc'

const builtinComponents = new Set(['KeepAlive', 'Teleport', 'Transition', 'TransitionGroup', 'Suspense'])
const kebabCase = name => name.replace(/([a-z0-9])([A-Z])/g, '$1-$2').replace(/([A-Z])([A-Z][a-z])/g, '$1-$2').toLowerCase()

export function componentReferences(source, filename = 'component.vue') {
  const { descriptor, errors } = parse(source, { filename })
  if (errors.length) throw new Error(`${filename}: ${errors[0]}`)
  const references = new Set()
  const visit = node => {
    if (node.type === 1 && /^[A-Z]/.test(node.tag) && !builtinComponents.has(node.tag)) references.add(node.tag)
    for (const child of node.children || []) visit(child)
  }
  if (descriptor.template?.ast) visit(descriptor.template.ast)
  if (!references.size) return []
  const script = descriptor.script || descriptor.scriptSetup ? compileScript(descriptor, { id: filename }) : null
  for (const name of references) {
    if (!script?.imports?.[name]) throw new Error(`${filename}: 自定义组件 ${name} 未显式导入，微信页面会丢失组件内容`)
  }
  return [...references].map(name => ({ name, tag: kebabCase(name) }))
}

export function vueFiles(directory) {
  if (!existsSync(directory)) return []
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? vueFiles(path) : path.endsWith('.vue') ? [path] : []
  })
}

// A successful JS build alone does not guarantee custom tags exist in WeChat.
export function verifyWechatComponents(sourceRoot, buildRoot) {
  let count = 0
  const appFile = join(buildRoot, 'app.json')
  const globalComponents = existsSync(appFile) ? JSON.parse(readFileSync(appFile, 'utf8')).usingComponents || {} : {}
  for (const sourcePath of vueFiles(sourceRoot)) {
    const references = componentReferences(readFileSync(sourcePath, 'utf8'), sourcePath)
    if (!references.length) continue
    const outputPath = join(buildRoot, relative(sourceRoot, sourcePath).replace(/\.vue$/, ''))
    const wxml = readFileSync(outputPath + '.wxml', 'utf8')
    const components = { ...globalComponents, ...JSON.parse(readFileSync(outputPath + '.json', 'utf8')).usingComponents }
    for (const { name, tag } of references) {
      // Platform conditional compilation may deliberately omit a component.
      if (!new RegExp(`<${tag}(?:\\s|/?>)`).test(wxml)) continue
      const registered = components[tag]
      if (!registered) throw new Error(`${outputPath}.json: usingComponents 缺少 ${tag}，请修复组件导入后重新构建`)
      const componentPath = registered.startsWith('/') ? join(buildRoot, registered.slice(1)) : resolve(dirname(outputPath), registered)
      const componentWxml = readFileSync(componentPath + '.wxml', 'utf8')
      if (name === 'ReferralForm') {
        for (const label of ['公司 / 品牌名称', '对接人职位', '联系手机', '销售平台', '产品重量', '指定快递承运商', '平均包裹产品数量', '现有仓配现状']) {
          if (!componentWxml.includes(label)) throw new Error(`ReferralForm 构建缺少表单内容：${label}`)
        }
        if (!/<slot\b/.test(componentWxml) || !/<button\b[^>]*bindtap=/.test(componentWxml)) {
          throw new Error('ReferralForm 构建缺少同意插槽或提交按钮')
        }
      }
      count++
    }
  }
  return count
}
