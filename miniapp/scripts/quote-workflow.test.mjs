import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { parse } from 'vue/compiler-sfc'
import * as quote from '../src/utils/quote-intake.mjs'
import * as common from '../src/utils/referral-intake.mjs'
import { prepareRateCard } from '../src/utils/quote-rate-card.mjs'

const source = readFileSync(new URL('../src/pages/quote-intake/index.vue',import.meta.url),'utf8')
const script = source.split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm,'')
const token = 'a'.repeat(32)
function page(api) {
  let open
  const context = vm.createContext({...quote,...common,ref:value => ({value}),reactive:value => value,
    computed:get => ({get value(){return get()}}),onLoad:fn=>{open=fn},publicQuoteRequest:api})
  vm.runInContext(script,context)
  return {open,run:js => vm.runInContext(js,context)}
}

test('quote form offers 12 groups, exact courier weight bands and distinct 27 services',() => {
  assert.equal(quote.QUOTE_FIELDS.length,12)
  assert.equal(new Set(quote.SERVICE_GROUPS.flatMap(g=>g.values)).size,27)
  assert.deepEqual(quote.QUOTE_FIELDS.find(f=>f.key==='weightRanges').options.map(o=>o.value),
    ['0-0.3','0.3-0.5','0.5-1','1-1.5','1.5-2','2-2.5','2.5-3','>3'])
  assert.equal(quote.QUOTE_FIELDS.find(f=>f.key==='peakOrders').options.at(-1).value,'>=50000')
  assert.deepEqual(quote.QUOTE_FIELDS.filter(f=>f.other).map(f=>f.other),['salesPlatformOther','productCategoryOther','erpOther','courierOther'])
})
test('optional ranges render real options, can be selected and cleared, and never submit an empty sentinel',() => {
  const form = quote.newQuoteForm()
  form.palletCount = '0'
  const nodes = []
  function visit(node) { nodes.push(node); for (const child of node.children || []) visit(child) }
  visit(parse(source).descriptor.template.ast)
  const event = (tag, type) => nodes.find(n=>n.tag===tag && n.props.some(p=>p.name==='on' && p.arg?.content===type && p.exp?.content.includes('form[f.key]'))).props.find(p=>p.name==='on').exp.content
  const change = event('radio-group','change'), clear = event('button','click')
  for (const f of quote.QUOTE_FIELDS.filter(f=>f.type==='single')) {
    assert.equal(form[f.key],'')
    assert.ok(f.options.every(o=>o.value && o.label!=='暂不填写'))
    const context = {form,f,$event:{detail:{value:f.options.at(-1).value}}}
    vm.runInNewContext(change,context)
    assert.equal(quote.quotePayload(form)[f.key],f.options.at(-1).value)
    vm.runInNewContext(clear,context)
    assert.equal(f.key in quote.quotePayload(form),false)
  }
  assert.equal(quote.quotePayload(form).palletCount,0)
})
test('conditional values are removed when unchecked and zero percent stays visible',() => {
  const f=quote.newQuoteForm()
  Object.assign(f,{salesPlatforms:['其他'],salesPlatformOther:'独立站',services:['收货清点上架'],serviceQuantities:{inbound:'500箱',packing:'500套'},
    heavyWeightKg:4,weightRanges:['2.5-3'],itemsPerParcel:'4',batchPercent:'0',erpOther:'过期的值'})
  const body=quote.quotePayload(f), rows=quote.quoteRows(body)
  assert.deepEqual(body.serviceQuantities,{inbound:'500箱'})
  assert.equal('heavyWeightKg' in body,false)
  assert.equal('erpOther' in body,false)
  assert.ok(rows.some(r=>r.value==='其他（独立站）'))
  assert.ok(rows.some(r=>r.value==='0%'))
  f.weightRanges=['>3']; f.heavyWeightKg='3'
  assert.throws(()=>quote.quotePayload(f),/常见重量/)
  assert.throws(()=>quote.quotePayload(quote.newQuoteForm()),/至少填写一项/)
})
test('public QR requires consent and retries the same immutable submission after a lost response',async () => {
  const calls=[], client=page(async (method,t,body) => {
    assert.equal(t,token)
    if (method==='GET') return {stage:'intent',submitted:false}
    calls.push(JSON.stringify(body))
    if (calls.length===1) throw Object.assign(new Error('timeout'),{code:'NETWORK_TIMEOUT'})
    return {submitted:true}
  })
  await client.open({scene:encodeURIComponent(token)})
  client.run("form.weightRanges=['0-0.3']; changeService(SERVICE_GROUPS[0],['收货清点上架']); changeService(SERVICE_GROUPS[2],['退件回仓上架'])")
  await client.run('send()')
  assert.equal(calls.length,0)
  client.run('agreed.value=true')
  await client.run('send()')
  assert.equal(client.run('state.value'),'ready')
  client.run("form.weightRanges=['>3']")
  await client.run('send()')
  assert.equal(calls[0],calls[1])
  const result=JSON.parse(calls[1])
  assert.deepEqual(result.requirements.weightRanges,['0-0.3'])
  assert.deepEqual(result.requirements.services,['收货清点上架','退件回仓上架'])
  assert.equal(result.agreed,true)
  assert.equal(client.run('state.value'),'done')
})
test('invalid code is rejected locally and already submitted quote shows a receipt',async () => {
  let calls=0
  const client=page(async ()=>{calls++;return {stage:'intent',submitted:true}})
  await client.open({scene:'invalid'})
  assert.equal(calls,0)
  assert.equal(client.run('state.value'),'error')
  await client.open({token})
  assert.equal(client.run('state.value'),'done')
  await client.run('send()')
  assert.equal(calls,1)
})
test('merchant card distinguishes missing from free, selects one heavy rule and drops UI-only keys',() => {
  const base={scopeNote:'全国（偏远另议），有效至年底',fields:{material:'',packSingle:'0'},services:{},carriers:[
    {rowKey:42,name:'顺丰',rates:{'0-0.3':'3.25'},heavyRule:'firstStep',heavyLabelFee:3,heavyFirstKg:5,heavyFirstFee:11,heavyStepKg:1,heavyStepFee:2}
  ]}
  const body=prepareRateCard(base)
  assert.deepEqual(body.fields,{packSingle:0})
  const byKg=prepareRateCard({...base,carriers:[{name:'圆通',rates:{},heavyLabelFee:0,heavyPerKg:1.2,heavyMaxKg:25}]})
  assert.equal(byKg.carriers[0].heavyMaxKg,25)
  assert.deepEqual(body.carriers[0],{name:'顺丰',rates:{'0-0.3':3.25},scopeNote:'',heavyRule:'firstStep',heavyFirstKg:5,heavyFirstFee:11,heavyStepKg:1,heavyStepFee:2})
  assert.throws(()=>prepareRateCard({...base,carriers:[...base.carriers,...base.carriers]}),/重复/)
  assert.throws(()=>prepareRateCard({...base,fields:{material:-1}}),/非负/)
})
