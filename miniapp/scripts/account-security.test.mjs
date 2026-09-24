import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
const source=readFileSync(new URL('../src/pages/account-security/index.vue',import.meta.url),'utf8').split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm,'')
for(const role of ['mp','wh'])test(`${role} 设置密码成功后仅清理本身份会话，并要求重新登录`,async()=>{
  const calls={mp:0,wh:0,status:0,setup:0},api={passwordStatus:async()=>{calls.status++;return {configured:false}},passwordSetup:async()=>{calls.setup++}}
  const context=vm.createContext({ref:value=>({value}),reactive:v=>v,onLoad:()=>{},authApi:api,warehouseAuthApi:api,token:{clear:()=>calls.mp++},warehouseToken:{clear:()=>calls.wh++},uni:{showModal:options=>{calls.modal=options;options.complete()},reLaunch:options=>calls.route=options.url}})
  vm.runInContext(source+`\nwarehouse.value=${role==='wh'};ready.value=true;form.username='123';form.password='123';confirmation.value='123';`,context)
  await vm.runInContext('save()',context)
  assert.equal(calls.setup,1);assert.equal(calls.status,0);assert.equal(calls.mp,role==='mp'?1:0);assert.equal(calls.wh,role==='wh'?1:0)
  assert.equal(calls.route,role==='wh'?'/pages/warehouse-login/index':'/pages/login/index');assert.match(calls.modal.content,/重新登录/)
  await vm.runInContext('save()',context);assert.equal(calls.setup,1)
})

for (const role of ['mp', 'wh']) {
  for (const configured of [false, true]) {
    test(`${role} ${configured ? '修改' : '设置'}账号密码接受短密码、中文符号与完整长密码`, async () => {
      for (const [username, password] of [['123', '123'], ['园区 伙伴@1', '密'], ['😀'.repeat(512), '密码🔑'.repeat(300) + '尾']]) {
        let submitted
        const api = { passwordSetup: async data => { submitted = data } }
        const context = vm.createContext({ ref: value => ({ value }), reactive: v => v, onLoad: () => {}, authApi: api, warehouseAuthApi: api,
          token: { clear: () => {} }, warehouseToken: { clear: () => {} }, uni: { showModal: () => {} }, input: { username, password } })
        vm.runInContext(source + `\nwarehouse.value=${role === 'wh'};configured.value=${configured};ready.value=true;Object.assign(form,input);form.currentPassword='123';confirmation.value=input.password;`, context)
        await vm.runInContext('save()', context)
        assert.equal(submitted?.username, username)
        assert.equal(submitted?.password, password)
        assert.equal(submitted?.currentPassword, '123')
      }
    })
  }
}

test('账号与密码仍必须填写，修改时必须提供当前密码，确认密码必须一致', async () => {
  for (const [username, password, confirmation, currentPassword, expected] of [
    ['', '123', '123', '123', '请输入账号'],
    ['123', '', '', '123', '请输入密码'],
    ['123', '123', '124', '123', '两次输入的密码不一致'],
    ['123', '123', '123', '', '修改密码需要填写当前密码'],
  ]) {
    const context = vm.createContext({ ref: value => ({ value }), reactive: v => v, onLoad: () => {}, authApi: { passwordSetup: () => assert.fail('不能提交无效表单') },
      warehouseAuthApi: {}, token: {}, warehouseToken: {}, values: { username, password, currentPassword }, confirmed: confirmation })
    vm.runInContext(source + '\nready.value=true;configured.value=true;Object.assign(form,values);confirmation.value=confirmed;', context)
    await vm.runInContext('save()', context)
    assert.equal(vm.runInContext('error.value', context), expected)
  }
})
test('修改密码失败时保留原会话和表单以便重试',async()=>{
  let cleared=false;const context=vm.createContext({ref:value=>({value}),reactive:v=>v,onLoad:()=>{},authApi:{passwordSetup:async()=>{throw new Error('当前密码错误')}},warehouseAuthApi:{},token:{clear:()=>cleared=true},warehouseToken:{clear:()=>cleared=true},uni:{showModal:()=>assert.fail('失败不能跳转')}})
  vm.runInContext(source+`\nready.value=true;configured.value=true;form.username='testuser';form.password='password123';form.currentPassword='wrong';confirmation.value='password123';`,context)
  await vm.runInContext('save()',context);assert.equal(cleared,false);assert.equal(vm.runInContext('error.value',context),'当前密码错误');assert.equal(vm.runInContext('ready.value',context),true)
})
