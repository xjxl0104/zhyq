#!/bin/bash
# SessionStart hook:每次开新会话自动 git fetch,工作区干净时把当前分支快进到远端最新。
# stdout 会注入会话上下文,让 Claude 开局就知道本地代码新旧;有问题只提示、不硬动。
# 手动等效命令:/sync
set -u

cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
command -v git >/dev/null 2>&1 || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

# macOS 默认没有 timeout(GNU coreutils),有就用,没有就裸跑
TO=""
command -v timeout >/dev/null 2>&1 && TO="timeout 30"

if ! $TO git fetch origin --quiet 2>/dev/null; then
    echo "⚠️ [自动同步] git fetch origin 失败(网络?)。本地代码可能不是最新——动手前先手动跑一次 git fetch origin 确认。"
    exit 0
fi

BR=$(git branch --show-current)
if [ -z "$BR" ]; then
    echo "ℹ️ [自动同步] 当前处于 detached HEAD,未同步。"
    exit 0
fi
if ! git rev-parse --verify -q "origin/$BR" >/dev/null; then
    echo "ℹ️ [自动同步] 分支 $BR 没有远端对应分支,未同步。"
    exit 0
fi

BEHIND=$(git rev-list --count "HEAD..origin/$BR" 2>/dev/null || echo 0)
AHEAD=$(git rev-list --count "origin/$BR..HEAD" 2>/dev/null || echo 0)

if [ "$BEHIND" -eq 0 ]; then
    echo "✅ [自动同步] $BR 已是最新:$(git log -1 --format='%h %s' | cut -c1-72)"
elif [ -n "$(git status --porcelain)" ]; then
    echo "⚠️ [自动同步] $BR 落后 origin/$BR ${BEHIND} 个提交,但工作区有未提交改动,未自动合并。处理改动后执行:git merge --ff-only origin/$BR"
elif [ "$AHEAD" -gt 0 ]; then
    echo "⚠️ [自动同步] $BR 与 origin/$BR 分叉(领先 ${AHEAD}/落后 ${BEHIND}),未自动合并,需人为决定 merge 还是 rebase。"
elif git merge --ff-only "origin/$BR" >/dev/null 2>&1; then
    echo "✅ [自动同步] 已把 $BR 快进 ${BEHIND} 个提交至远端最新:$(git log -1 --format='%h %s' | cut -c1-72)"
else
    echo "⚠️ [自动同步] $BR 快进失败,保持原状,请手动检查 git status。"
fi

if [ "$BR" = "main" ]; then
    echo "ℹ️ [自动同步] 注意:当前在 main(生产分支,合入即上线)。日常开发请切到 test:git checkout test"
fi
exit 0
