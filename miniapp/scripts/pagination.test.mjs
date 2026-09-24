import test from 'node:test'
import assert from 'node:assert/strict'
import { createPager } from '../src/utils/pagination.mjs'
const pending=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
test('客户、佣金与提现可翻过原 100 条上限并在末页停止',async()=>{
  for(const kind of ['customers','commissions','withdrawals']){
    const calls=[],state={};const pager=createPager(async({pageNo,pageSize})=>{calls.push(pageNo);return {total:105,records:Array.from({length:Math.min(pageSize,105-(pageNo-1)*pageSize)},(_,i)=>({id:(pageNo-1)*pageSize+i+1,kind}))}},state)
    await pager.load();for(let i=0;i<6;i++)await pager.load(false)
    assert.equal(state.records.length,105);assert.deepEqual(calls,[1,2,3,4,5,6]);assert.equal(state.records[104].id,105)
  }
})
test('加载下一页失败保留已有记录并重试同一页，不跳页',async()=>{
  const state={},calls=[];let fail=true;const pager=createPager(async({pageNo})=>{calls.push(pageNo);if(pageNo===2&&fail){fail=false;throw new Error('网络超时')}return {records:[{id:pageNo}],total:2}},state,{pageSize:1})
  await pager.load();await pager.load(false);assert.equal(state.pageNo,1);assert.equal(state.error,'网络超时');assert.deepEqual(state.records,[{id:1}]);await pager.retry();assert.deepEqual(calls,[1,2,2]);assert.equal(state.pageNo,2);assert.equal(state.error,'')
})
test('新筛选结果覆盖旧请求，旧响应不得替换数据或提前取消加载',async()=>{
  const a=pending(),b=pending(),state={};const pager=createPager(({status})=>status===1?a.promise:b.promise,state)
  const old=pager.load(true,{status:1}),fresh=pager.load(true,{status:2});a.resolve({records:[{id:1}],total:1});await old;assert.equal(state.loading,true);assert.deepEqual(state.records,[])
  b.resolve({records:[{id:2}],total:1});await fresh;assert.deepEqual(state.records,[{id:2}]);assert.equal(state.loading,false)
})
test('旧筛选失败不能污染新筛选、触底并发只请求一次、卸载后忽略响应',async()=>{
  const a=pending(),b=pending(),state={};let count=0;const pager=createPager(({status})=>{count++;return status===1?a.promise:b.promise},state)
  const old=pager.load(true,{status:1}),fresh=pager.load(true,{status:2});await pager.load(false);assert.equal(count,2)
  a.reject(new Error('旧错误'));await old;assert.equal(state.error,'');pager.invalidate();b.resolve({records:[{id:2}],total:1});await fresh;assert.deepEqual(state.records,[])
})
test('空结果仅请求第一页且重叠分页去重',async()=>{
  let calls=0;const state={},pager=createPager(async()=>{calls++;return {records:[],total:0}},state);await pager.load();await pager.load(false);assert.equal(calls,1);assert.equal(state.loaded,true)
  const other={},pages=createPager(async({pageNo})=>({records:pageNo===1?[{id:1},{id:2}]:[{id:2},{id:3}],total:3}),other,{pageSize:2});await pages.load();await pages.load(false);assert.deepEqual(other.records.map(x=>x.id),[1,2,3])
})
