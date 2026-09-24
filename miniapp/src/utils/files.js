export function chooseDocument() {
  return new Promise((resolve, reject) => {
    const options = {
      count: 1, type: 'file',
      extension: ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'jpg', 'jpeg', 'png'],
      success: result => {
        const file = result.tempFiles?.[0]
        if (!file) return reject(new Error('未选择文件'))
        if (file.size > 20 * 1024 * 1024) return reject(new Error('文件不能超过20MB'))
        resolve({ path: file.path || result.tempFilePaths?.[0], name: file.name || '附件', size: file.size })
      },
      fail: error => reject(new Error(error.errMsg?.includes('cancel') ? '已取消选择' : '无法选择文件，请重试'))
    }
    // #ifdef MP-WEIXIN
    uni.chooseMessageFile(options)
    // #endif
    // #ifdef H5
    uni.chooseFile(options)
    // #endif
  })
}
