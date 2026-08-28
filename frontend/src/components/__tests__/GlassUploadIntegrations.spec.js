import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import FileUpload from '../FileUpload.vue'
import VendingImportDialog from '@/views/app/components/VendingImportDialog.vue'
import ReceivableImportDialog from '@/views/finance/components/ReceivableImportDialog.vue'
import ResponsibleUnit from '@/views/property/ResponsibleUnit.vue'
import FeedbackFab from '@/views/suggestion/FeedbackFab.vue'
import MySuggestions from '@/views/suggestion/MySuggestions.vue'

vi.mock('@/api/file', () => ({
  uploadUrl: '/api/file/upload',
  fileApi: {
    remove: vi.fn().mockResolvedValue(undefined),
    download: vi.fn().mockResolvedValue({ data: new Blob() }),
    upload: vi.fn().mockResolvedValue({ id: 1, originalName: 'file.png' }),
  },
}))

vi.mock('@/api/property', () => ({
  responsibleUnitApi: {
    page: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  },
}))

vi.mock('@/api/suggestion', () => ({
  suggestionApi: {
    mine: vi.fn().mockResolvedValue({ records: [], total: 0 }),
    mineDetail: vi.fn(),
    submit: vi.fn(),
  },
}))

const container = {
  template: '<div><slot /><slot name="tip" /><slot name="footer" /></div>',
}

const upload = {
  name: 'ElUpload',
  template: '<div class="el-upload-stub"><slot /><slot name="tip" /></div>',
}

const stubs = {
  'el-dialog': container,
  'el-steps': container,
  'el-step': container,
  'el-form': container,
  'el-form-item': container,
  'el-select': container,
  'el-option': true,
  'el-button': container,
  'el-upload': upload,
  'el-alert': true,
  'el-table': container,
  'el-table-column': true,
  'el-scrollbar': container,
  'el-pagination': true,
  'el-icon': container,
  'el-radio-group': container,
  'el-radio': container,
  'el-input': true,
  'el-descriptions': container,
  'el-descriptions-item': container,
  'el-image': true,
  'el-timeline': container,
  'el-timeline-item': container,
  SuggestionWall: true,
  Search: true,
  Plus: true,
  Upload: true,
  UploadFilled: true,
  ChatDotRound: true,
}

function mountWithStubs(component, props = {}) {
  return mount(component, { props, global: { stubs } })
}

describe('glass upload integrations', () => {
  it('wraps the common and spreadsheet import uploads without replacing el-upload', () => {
    const common = mountWithStubs(FileUpload)
    const vending = mountWithStubs(VendingImportDialog, { modelValue: true })
    const receivable = mountWithStubs(ReceivableImportDialog, { modelValue: true })

    for (const wrapper of [common, vending, receivable]) {
      expect(wrapper.findAll('.glass-surface--upload')).toHaveLength(1)
      expect(wrapper.findAll('.el-upload-stub')).toHaveLength(1)
    }
  })

  it('wraps the responsible unit import upload', async () => {
    const wrapper = mountWithStubs(ResponsibleUnit)
    await flushPromises()

    expect(wrapper.findAll('.glass-surface--upload')).toHaveLength(1)
    expect(wrapper.findAll('.el-upload-stub')).toHaveLength(1)
  })

  it('wraps screenshot and attachment uploads independently', async () => {
    const feedback = mountWithStubs(FeedbackFab)
    const suggestions = mountWithStubs(MySuggestions)
    await flushPromises()

    expect(feedback.findAll('.glass-surface--upload')).toHaveLength(1)
    expect(feedback.findAll('.el-upload-stub')).toHaveLength(1)
    expect(suggestions.findAll('.glass-surface--upload')).toHaveLength(2)
    expect(suggestions.findAll('.el-upload-stub')).toHaveLength(2)
  })
})
