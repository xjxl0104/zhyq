// Only use for transport failures (uni fail / fetch rejection), never HTTP business responses.
export function createNetworkError(cause) {
  const errMsg = typeof cause === 'string' ? cause : String(cause?.errMsg || cause?.message || '')
  let code = 'NETWORK_UNKNOWN'
  let message = '网络请求未完成，请重试；持续失败请联系管理员'
  if (/not in (?:the )?domain list|domain.*(?:white.?list|not allowed)|url.*不在.*域名|合法域名|域名.*白名单/i.test(errMsg)) {
    code = 'NETWORK_DOMAIN'
    message = '服务域名未获微信允许，请联系管理员配置'
  } else if (/ssl|tls|certificate|cert[ _-]|handshake|证书|安全连接/i.test(errMsg)) {
    code = 'NETWORK_TLS'
    message = '服务安全连接失败，请联系管理员检查证书'
  } else if (/timeout|timed out|超时/i.test(errMsg)) {
    code = 'NETWORK_TIMEOUT'
    message = '连接超时，请稍后重试'
  } else if (/offline|not connected|network is (?:down|unreachable)|internet.*disconnected|无网络|断网|网络未连接/i.test(errMsg)) {
    code = 'NETWORK_OFFLINE'
    message = '当前网络未连接，请检查手机网络'
  } else if (/connect|connection|failed to fetch|networkerror|network error|network request failed|resolve host|dns|连接失败/i.test(errMsg)) {
    code = 'NETWORK_CONNECTION'
    message = '无法连接服务，请检查网络或稍后重试'
  }
  const error = new Error(message)
  error.name = 'NetworkError'
  error.code = code
  error.errMsg = errMsg
  error.cause = cause
  return error
}
