<script setup>
import { onLaunch } from '@dcloudio/uni-app'
onLaunch(() => {})
</script>

<style>
page { background: #f5f6fa; font-size: 28rpx; color: #1f2329; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin: 20rpx; }
.row { display: flex; align-items: center; justify-content: space-between; padding: 20rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.row:last-child { border-bottom: none; }
.muted { color: #8f959e; font-size: 24rpx; }
.money { font-weight: 600; font-variant-numeric: tabular-nums; }
.btn { background: #4f46e5; color: #fff; border-radius: 12rpx; margin: 20rpx; }
.btn[disabled] { opacity: .5; }
.tag { font-size: 22rpx; padding: 4rpx 12rpx; border-radius: 8rpx; background: #eef0ff; color: #4f46e5; }
.tag.warn { background: #fff4e5; color: #b26a00; }
.tag.ok { background: #e8f7ee; color: #1a7f37; }
.input { background: #f5f6fa; border-radius: 12rpx; padding: 20rpx; margin: 12rpx 0; }
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20rpx; }
.stat { background: #fff; border-radius: 16rpx; padding: 24rpx; }
.stat .v { font-size: 40rpx; font-weight: 600; margin-top: 8rpx; }
</style>
