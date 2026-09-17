import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fileApi } from '@/api/file'
import { startFileDownload } from '../fileDownload'

vi.mock('@/api/file', () => ({ fileApi: { createDownloadTicket: vi.fn(), download: vi.fn() } }))

let clicked
beforeEach(() => {
  clicked = []
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () {
    clicked.push({ href: this.getAttribute('href'), name: this.download, attached: this.isConnected })
  })
})
afterEach(() => vi.restoreAllMocks())

describe('browser attachment download', () => {
  it('hands a short-lived URL to the browser as soon as authorization finishes', async () => {
    let authorize
    fileApi.createDownloadTicket.mockReturnValue(new Promise(resolve => { authorize = resolve }))
    const download = startFileDownload(35, '合同.pdf')
    expect(clicked).toEqual([])
    authorize({ ticket: 'one-time-ticket' })
    await download
    expect(clicked).toEqual([{
      href: '/api/file/browser-download/35?ticket=one-time-ticket', name: '合同.pdf', attached: true
    }])
    expect(fileApi.download).not.toHaveBeenCalled()
    expect(document.querySelector('a[download]')).toBeNull()
  })

  it('does not start a browser download when authorization fails', async () => {
    fileApi.createDownloadTicket.mockRejectedValue(new Error('未登录'))
    await expect(startFileDownload(35, '合同.pdf')).rejects.toThrow('未登录')
    expect(clicked).toEqual([])
  })
})
