/**
 * 平台应用日报「使用时长」心跳（AI 中台 dgsd2025/ai-gateway 的 lib/heartbeat.mjs 接收）。
 * 页面在前台、且 5 分钟内动过鼠标或键盘时，每分钟报一次「还在用」；切走、锁屏、挂着不动都不报。
 * 只报门户 uid 和姓名（/auth/my-access 给的），不报页面内容；没绑门户的账号不报；失败静默。
 * 本地开发不报（localhost 不在 AI 中台的来源白名单里）。
 */
const ENDPOINT = 'https://token.vvaix.com/api/hb'
const IDLE_MS = 5 * 60_000
const ACTIVITY = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'wheel']

let who = null
let last = Date.now()
let timer

function beat() {
  if (!who || document.visibilityState !== 'visible' || Date.now() - last > IDLE_MS) return
  fetch(ENDPOINT, {
    method: 'POST', mode: 'cors', credentials: 'omit', keepalive: true,
    headers: { 'content-type': 'text/plain' }, body: JSON.stringify(who),
  }).catch(() => undefined)
}

/** 登录后传 { portalUid, name }；退出登录传 null 停报。 */
export function startHeartbeat(user) {
  if (import.meta.env.DEV) return
  who = user && user.portalUid ? { u: String(user.portalUid), n: String(user.name ?? '') } : null
  if (!who) return
  if (timer === undefined) {
    for (const e of ACTIVITY) addEventListener(e, () => { last = Date.now() }, { passive: true, capture: true })
    timer = setInterval(beat, 60_000)
  }
  beat()
}
