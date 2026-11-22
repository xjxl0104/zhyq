import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import CustomerRequirements from '../CustomerRequirements.vue'
import { emptyRequirements, validateRequirements, requirementsRows } from '../customerRequirements'

function form(initial = {}) {
  const wrapper = mount(CustomerRequirements, {
    props: {
      modelValue: { ...emptyRequirements(), ...initial },
      'onUpdate:modelValue': value => wrapper.setProps({ modelValue: value })
    },
    global: { stubs: { ElFormItem: { template: '<section><slot /></section>' } } }
  })
  return wrapper
}

async function check(wrapper, label) {
  const checkbox = wrapper.findAll('.el-checkbox').find(item => item.find('.el-checkbox__label').text() === label)
  expect(checkbox, `缺少勾选项：${label}`).toBeTruthy()
  await checkbox.find('input[type="checkbox"]').setValue(!checkbox.find('input').element.checked)
}

describe('客户仓配需求勾选表单', () => {
  it('销售品类支持多选，并要求填写其他品类', async () => {
    const wrapper = form()
    await check(wrapper, '食品饮料')
    await check(wrapper, '美妆护肤')
    expect(wrapper.props('modelValue').productCategories).toEqual(['食品饮料', '美妆护肤'])
    // “其他”在多个组中存在，限定到销售品类组。
    const categories = wrapper.findAll('section').find(section => section.text().includes('食品饮料'))
    const other = categories.findAll('.el-checkbox').find(item => item.find('.el-checkbox__label').text() === '其他')
    await other.find('input').setValue(true)
    expect(validateRequirements(wrapper.props('modelValue'))).toBe('请填写其他销售品类')
    await categories.find('input[placeholder="其他销售品类"]').setValue('户外装备')
    expect(validateRequirements(wrapper.props('modelValue'))).toBe('')
    expect(requirementsRows(wrapper.props('modelValue'))).toContainEqual(['销售品类', '食品饮料、美妆护肤、其他：户外装备'])
    wrapper.unmount()
  })

  it('SKU 和日均单量区间展开勾选，保持单选且可以取消', async () => {
    const wrapper = form()
    await check(wrapper, '0–小于 10 个')
    await check(wrapper, '20–小于 50 个')
    expect(wrapper.props('modelValue').skuCount).toBe('20-50')
    const old = wrapper.findAll('.el-checkbox').find(item => item.text().includes('0–小于 10 个'))
    expect(old.find('input').element.checked).toBe(false)
    await check(wrapper, '20–小于 50 个')
    expect(wrapper.props('modelValue').skuCount).toBe('')
    await check(wrapper, '≥10000 单')
    expect(wrapper.props('modelValue').dailyOrders).toBe('>=10000')
    // 表单仅有仓配方式保留下拉，业务规模不再需要展开选择器。
    expect(wrapper.findAll('.el-select')).toHaveLength(1)
    wrapper.unmount()
  })

  it('旧平均件数不会因编辑其他信息丢失，主动选区间后替换且清空不复活旧值', async () => {
    const wrapper = form({ itemsPerParcel: 2.5, productCount: '10-20' })
    expect(wrapper.text()).toContain('历史填写：2.5 件')
    await check(wrapper, '食品饮料')
    expect(wrapper.props('modelValue')).toMatchObject({ itemsPerParcel: 2.5, productCount: '10-20' })
    await check(wrapper, '2–小于 5 件')
    expect(wrapper.props('modelValue')).toMatchObject({ itemsPerParcelRange: '2-5', itemsPerParcel: null })
    await check(wrapper, '2–小于 5 件')
    expect(wrapper.props('modelValue')).toMatchObject({ itemsPerParcelRange: '', itemsPerParcel: null })
    expect(wrapper.text()).not.toContain('历史填写：')
    wrapper.unmount()
  })

  it('详情优先显示新件数范围，并将历史产品种类数与销售品类分开', () => {
    const rows = requirementsRows({ productCount: '10-20', productCategories: ['日化清洁'], itemsPerParcel: 2.5, itemsPerParcelRange: '>=20' })
    expect(rows).toContainEqual(['销售品类', '日化清洁'])
    expect(rows).toContainEqual(['销售产品种类数（历史）', '10–小于 20 种'])
    expect(rows).toContainEqual(['平均包裹产品数量', '≥20 件'])
    expect(requirementsRows({ itemsPerParcel: 2.5 })).toContainEqual(['平均包裹产品数量', '2.5 件'])
    expect(validateRequirements({ ...emptyRequirements(), productCategories: ['非法选项'] })).toContain('销售品类')
    expect(validateRequirements({ ...emptyRequirements(), itemsPerParcelRange: '>=100' })).toContain('平均包裹产品数量')
  })
})
