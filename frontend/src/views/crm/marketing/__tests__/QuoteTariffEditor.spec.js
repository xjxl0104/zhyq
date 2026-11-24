import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { expect, it } from 'vitest'
import ElementPlus from 'element-plus'
import QuoteTariffEditor from '../QuoteTariffEditor.vue'

it('edits an explicit zero in the selected province and retains other provinces when filtered',async()=>{
  const cells=reactive({'yt.2':{w0:'12'}})
  const sections=[{key:'yt',label:'圆通',columns:[{key:'w0',label:'0–0.3 kg',kind:'number'}],
    rows:[{id:'yt.1',label:'广东'},{id:'yt.2',label:'西藏'}]}]
  const wrapper=mount(QuoteTariffEditor,{props:{sections,cells,editable:true},global:{plugins:[ElementPlus]}})
  try {
    await flushPromises()
    await wrapper.get('input[placeholder="查找省份、规格或收费项目"]').setValue('广东')
    await flushPromises()
    expect(wrapper.findAll('input[placeholder="待询价"]')).toHaveLength(1)
    await wrapper.get('input[placeholder="待询价"]').setValue('0')
    expect(cells).toEqual({'yt.1':{w0:'0'},'yt.2':{w0:'12'}})
    await wrapper.setProps({editable:false})
    expect(wrapper.get('input[placeholder="待询价"]').element.disabled).toBe(true)
  } finally {wrapper.unmount()}
})
