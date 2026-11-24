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
    expect(wrapper.findAll('.el-select')).toHaveLength(0)
    wrapper.unmount()
  })

  it('旧平均件数不会因编辑其他信息丢失，主动选区间后替换且清空不复活旧值', async () => {
    const wrapper = form({ itemsPerParcel: 2.5, productCount: '10-20' })
    expect(wrapper.text()).toContain('历史填写：2.5 件')
    await check(wrapper, '食品饮料')
    expect(wrapper.props('modelValue')).toMatchObject({ itemsPerParcel: 2.5, productCount: '10-20' })
    await check(wrapper, '2 件')
    expect(wrapper.props('modelValue')).toMatchObject({ itemsPerParcelRange: '2', itemsPerParcel: null })
    await check(wrapper, '2 件')
    expect(wrapper.props('modelValue')).toMatchObject({ itemsPerParcelRange: '', itemsPerParcel: null })
    expect(wrapper.text()).not.toContain('历史填写：')
    wrapper.unmount()
  })
  it('仓配方式单选勾选且可取消，旧件数区间显示历史值而不误选新档位', async () => {
    const wrapper = form({ itemsPerParcelRange: '2-5' })
    expect(wrapper.text()).toContain('历史填写：2–小于 5 件')
    await check(wrapper, '自建仓')
    await check(wrapper, '外包云仓')
    expect(wrapper.props('modelValue').warehouseMode).toBe('外包云仓')
    expect(wrapper.props('modelValue').itemsPerParcelRange).toBe('2-5')
    expect(validateRequirements(wrapper.props('modelValue'))).toBe('')
    await check(wrapper, '外包云仓')
    expect(wrapper.props('modelValue').warehouseMode).toBe('')
    await check(wrapper, '6 件及以上')
    expect(wrapper.props('modelValue').itemsPerParcelRange).toBe('>=6')
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

  it('报备表缺失的销售平台、品类、重量、SKU、日均单量、包裹件数与指定快递自动从已提交报价需求合并回显', () => {
    const rows = requirementsRows(
      { warehouseMode: '外包云仓', currentCouriers: ['圆通', '中通'], painPoints: ['错发漏发多'] },
      {
        salesPlatforms: ['抖音', '天猫'],
        productCategories: ['美妆护肤'],
        weightRanges: ['0-0.3', '0.3-0.5', '>3'],
        heavyWeightKg: 5,
        skuCount: '100-500',
        dailyOrders: '3000-5000',
        itemsPerParcel: 3,
        couriers: ['顺丰']
      }
    )
    expect(rows).toContainEqual(['销售平台', '抖音、天猫'])
    expect(rows).toContainEqual(['销售品类', '美妆护肤'])
    expect(rows).toContainEqual(['产品重量', '≤0.3 kg、＞0.3 至 0.5 kg、＞3 kg；典型重件 5 kg'])
    expect(rows).toContainEqual(['SKU 数量', '100–小于 500 个'])
    expect(rows).toContainEqual(['日均单量', '3000–小于 5000 单'])
    expect(rows).toContainEqual(['平均包裹产品数量', '3 件'])
    expect(rows).toContainEqual(['指定快递承运商', '顺丰'])
    expect(rows).toContainEqual(['目前仓配方式', '外包云仓'])
  })
})
