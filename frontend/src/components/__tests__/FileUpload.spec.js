import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'

import FileUpload from '../FileUpload.vue'

/**
 * 上传参数里的 bizId 处理。
 *
 * 由来:组件原先无条件把 bizId 塞进 el-upload 的 data。新建单据时还没有 ID、
 * bizId 是 null,而 FormData 只装字符串 —— null 被转成字面量 "null" 发给后端,
 * `@RequestParam Long bizId` 拿它转 Long 直接抛:
 *   NumberFormatException: For input string: "null"
 * 整个上传返回 500,于是「先传后回填」没有东西可回填,详情页永远显示「暂无附件」。
 * 编辑已有单据时 bizId 有值,反而正常 —— 所以问题只在新建流程出现。
 */
const mountUpload = (props) => mount(FileUpload, {
  props: { bizType: 'budget', ...props },
  global: { plugins: [ElementPlus] }
})

const uploadData = (wrapper) => wrapper.findComponent({ name: 'ElUpload' }).props('data')

describe('FileUpload 的 bizId 上传参数', () => {
  it('bizId 为 null 时不带该字段（新建单据）', () => {
    const data = uploadData(mountUpload({ bizId: null }))

    expect(data).toEqual({ bizType: 'budget' })
    expect('bizId' in data).toBe(false)
  })

  it('未传 bizId 时同样不带该字段', () => {
    const data = uploadData(mountUpload({}))

    expect('bizId' in data).toBe(false)
  })

  it('bizId 为空字符串时不带该字段', () => {
    const data = uploadData(mountUpload({ bizId: '' }))

    expect('bizId' in data).toBe(false)
  })

  it('bizId 有值时正常携带（编辑已有单据）', () => {
    const data = uploadData(mountUpload({ bizId: 42 }))

    expect(data).toEqual({ bizType: 'budget', bizId: 42 })
  })

  /** 0 是合法主键起点,不能被当成空值过滤掉 */
  it('bizId 为 0 时仍然携带', () => {
    const data = uploadData(mountUpload({ bizId: 0 }))

    expect(data.bizId).toBe(0)
  })
})
