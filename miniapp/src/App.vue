<script setup>
import { onLaunch } from '@dcloudio/uni-app'
onLaunch(() => {})
</script>

<style>
/* DIPARK / 智慧园区移动端基础视觉：深靛蓝、园区蓝、暖金状态色 */
page {
  --park-ink: #111936;
  --park-ink-2: #1d2a57;
  --park-blue: #3857f5;
  --park-blue-2: #637bff;
  --park-cyan: #50c7d8;
  --park-gold: #f5b84b;
  --park-bg: #f4f6fb;
  --park-card: #ffffff;
  --park-line: #e6eaf3;
  --park-text: #17203e;
  --park-muted: #77809a;
  background: var(--park-bg);
  color: var(--park-text);
  font-size: 28rpx;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "Helvetica Neue", "PingFang SC", sans-serif;
  -webkit-font-smoothing: antialiased;
}

view, text, button, input, textarea, picker { box-sizing: border-box; }
button::after { border: 0; }
button { font-family: inherit; }

.page-wrap { min-height: 100vh; padding: 8rpx 0 40rpx; }
.card {
  position: relative;
  margin: 24rpx;
  padding: 30rpx;
  overflow: hidden;
  background: var(--park-card);
  border: 1rpx solid rgba(28, 42, 87, .07);
  border-radius: 24rpx;
  box-shadow: 0 12rpx 36rpx rgba(29, 42, 87, .06);
}
.card::after {
  position: absolute;
  top: 0;
  right: 0;
  width: 180rpx;
  height: 180rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(80, 199, 216, .12), rgba(80, 199, 216, 0) 68%);
  pointer-events: none;
  content: '';
}
.hero-card {
  margin-top: 16rpx;
  padding: 34rpx 32rpx 32rpx;
  color: #fff;
  border: 0;
  background: linear-gradient(135deg, #111936 0%, #1d2a57 58%, #2d4395 100%);
  box-shadow: 0 18rpx 44rpx rgba(17, 25, 54, .2);
}
.hero-card::before {
  position: absolute;
  right: -70rpx;
  bottom: -110rpx;
  width: 360rpx;
  height: 360rpx;
  border: 1rpx solid rgba(255,255,255,.14);
  border-radius: 50%;
  box-shadow: 0 0 0 26rpx rgba(255,255,255,.035), 0 0 0 52rpx rgba(255,255,255,.025);
  content: '';
}
.hero-card::after { background: radial-gradient(circle, rgba(80, 199, 216, .24), rgba(80, 199, 216, 0) 68%); }
.hero-card view, .hero-card text { position: relative; z-index: 1; }
.eyebrow { color: var(--park-cyan); font-size: 22rpx; letter-spacing: 3rpx; }
.hero-title { margin-top: 10rpx; font-size: 44rpx; font-weight: 700; letter-spacing: -1rpx; }
.hero-subtitle { margin-top: 8rpx; color: rgba(255,255,255,.68); font-size: 24rpx; }
.hero-number { margin: 24rpx 0 18rpx; font-size: 68rpx; font-weight: 700; letter-spacing: -2rpx; }
.hero-label { color: rgba(255,255,255,.7); font-size: 23rpx; }
.hero-meta { display: flex; gap: 12rpx; flex-wrap: wrap; }
.hero-meta-item { padding: 10rpx 16rpx; color: rgba(255,255,255,.82); border: 1rpx solid rgba(255,255,255,.15); border-radius: 999rpx; background: rgba(255,255,255,.08); font-size: 22rpx; }
.title, .section-title { color: var(--park-text); font-size: 32rpx; font-weight: 700; letter-spacing: -.4rpx; }
.section-head { display: flex; align-items: center; justify-content: space-between; margin: 32rpx 26rpx 16rpx; }
.section-head .section-title { font-size: 30rpx; }
.section-link { color: var(--park-blue); font-size: 24rpx; }
.muted { color: var(--park-muted); font-size: 24rpx; line-height: 1.55; }
.caption { color: var(--park-muted); font-size: 22rpx; }
.row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; min-height: 88rpx; padding: 18rpx 0; border-bottom: 1rpx solid var(--park-line); }
.row:last-child { border-bottom: 0; }
.money { color: var(--park-ink); font-weight: 700; font-variant-numeric: tabular-nums; }
.btn {
  min-height: 88rpx;
  margin: 20rpx 0 0;
  padding: 0 30rpx;
  color: #fff;
  border-radius: 16rpx;
  background: linear-gradient(135deg, var(--park-blue-2), var(--park-blue));
  box-shadow: 0 10rpx 22rpx rgba(56, 87, 245, .22);
  font-size: 28rpx;
  font-weight: 600;
  line-height: 88rpx;
}
.btn + .btn { margin-top: 14rpx; }
.btn.secondary { color: var(--park-blue); border: 1rpx solid rgba(56, 87, 245, .2); background: #f1f4ff; box-shadow: none; }
.btn.ghost { color: var(--park-text); border: 1rpx solid var(--park-line); background: #fff; box-shadow: none; }
.btn[disabled] { opacity: .45; box-shadow: none; }
.tag { display: inline-flex; align-items: center; min-height: 42rpx; padding: 0 14rpx; color: var(--park-blue); border: 1rpx solid rgba(56, 87, 245, .14); border-radius: 999rpx; background: #eef1ff; font-size: 22rpx; line-height: 42rpx; }
.tag.warn { color: #9a6400; border-color: rgba(245,184,75,.22); background: #fff7e6; }
.tag.ok { color: #147a62; border-color: rgba(40,169,133,.18); background: #eaf8f3; }
.tag.danger { color: #b64255; border-color: rgba(209,74,92,.16); background: #fff0f2; }
.input { width: 100%; min-height: 88rpx; margin: 14rpx 0; padding: 0 24rpx; color: var(--park-text); border: 1rpx solid var(--park-line); border-radius: 16rpx; background: #f8f9fc; font-size: 27rpx; line-height: 88rpx; }
textarea.input { min-height: 150rpx; padding-top: 22rpx; line-height: 1.5; }
.input:focus { border-color: var(--park-blue); background: #fff; }
.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18rpx; margin: 0 24rpx; }
.stat { position: relative; min-height: 152rpx; padding: 24rpx; overflow: hidden; border: 1rpx solid var(--park-line); border-radius: 20rpx; background: var(--park-card); box-shadow: 0 8rpx 24rpx rgba(29, 42, 87, .045); }
.stat::before { position: absolute; top: 0; left: 0; width: 58rpx; height: 6rpx; border-radius: 0 0 6rpx 0; background: var(--park-blue); content: ''; }
.stat:nth-child(2)::before { background: var(--park-cyan); }
.stat:nth-child(3)::before { background: var(--park-gold); }
.stat:nth-child(4)::before { background: #77a0ff; }
.stat .v { margin-top: 10rpx; color: var(--park-ink); font-size: 42rpx; font-weight: 700; letter-spacing: -.5rpx; }
.quick-card { display: flex; align-items: center; gap: 18rpx; min-height: 108rpx; padding: 20rpx; border: 1rpx solid var(--park-line); border-radius: 20rpx; background: #fff; box-shadow: 0 8rpx 22rpx rgba(29,42,87,.04); }
.quick-icon { display: flex; align-items: center; justify-content: center; width: 58rpx; height: 58rpx; flex: 0 0 58rpx; color: #fff; border-radius: 18rpx; background: linear-gradient(145deg, var(--park-blue-2), var(--park-blue)); font-size: 22rpx; font-weight: 700; }
.quick-card:nth-child(2) .quick-icon { background: linear-gradient(145deg, #64d2df, #299cb0); }
.quick-card:nth-child(3) .quick-icon { background: linear-gradient(145deg, #f8ca69, #df961c); }
.quick-card:nth-child(4) .quick-icon { background: linear-gradient(145deg, #839cff, #596bd4); }
.quick-title { color: var(--park-text); font-size: 27rpx; font-weight: 600; }
.quick-desc { margin-top: 5rpx; color: var(--park-muted); font-size: 21rpx; }
.list-empty { padding: 40rpx 10rpx; color: var(--park-muted); text-align: center; font-size: 25rpx; }
.form-note { padding: 18rpx 20rpx; color: #58617d; border-left: 6rpx solid var(--park-cyan); border-radius: 12rpx; background: #f1f8fa; font-size: 23rpx; line-height: 1.55; }
.brand-mark { display: flex; align-items: center; justify-content: center; width: 76rpx; height: 76rpx; color: #fff; border-radius: 24rpx; background: linear-gradient(145deg, #637bff, #3857f5); box-shadow: 0 12rpx 24rpx rgba(56,87,245,.28); font-size: 32rpx; font-weight: 800; }
.pressable { transition: opacity .15s ease, transform .15s ease; }
.pressable:active { opacity: .76; transform: scale(.985); }
.uni-page-head { color: var(--park-text) !important; background: var(--park-bg) !important; }
</style>
