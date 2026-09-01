#!/bin/bash
# PostToolUse(Bash) hook:Claude Code 成功执行 git push(推到 origin/GitHub)后,
# 自动把当前分支设为 GitHub 仓库默认分支。
# 只处理 ver* 命名的版本分支(feat/xxx 等实验分支不会动默认分支);
# 想对所有分支生效,删掉下面的 "ver*) ;;" 那段 case 即可。
set -uf

INPUT=$(cat)
CMD=$(printf '%s' "$INPUT" | jq -r '.tool_input.command // empty')

# 只处理包含 git push 的命令(push 失败时 CC 走 PostToolUseFailure,不会进到这里)
case "$CMD" in
  *"git push"*) ;;
  *) exit 0 ;;
esac

# 找出 push 的目标远端:push 后第一个非 "-" 开头的参数;没写就是 origin。
# 推到 prod(部署服务器)等其他远端时不动 GitHub 默认分支。
REMOTE=""
FOUND_PUSH=0
PREV=""
for tok in $CMD; do
  if [ "$FOUND_PUSH" = "1" ]; then
    case "$tok" in
      -*) continue ;;
      "&&"|"||"|";") break ;;
      *) REMOTE="$tok"; break ;;
    esac
  fi
  [ "$tok" = "push" ] && [ "$PREV" = "git" ] && FOUND_PUSH=1
  PREV="$tok"
done
[ -z "$REMOTE" ] && REMOTE="origin"
[ "$REMOTE" = "origin" ] || exit 0

# 到命令实际执行的目录里取当前分支(兼容 worktree)
CWD=$(printf '%s' "$INPUT" | jq -r '.cwd // empty')
[ -n "$CWD" ] && cd "$CWD" 2>/dev/null
BRANCH=$(git branch --show-current 2>/dev/null)
[ -n "$BRANCH" ] || exit 0

case "$BRANCH" in
  ver*) ;;
  *) exit 0 ;;
esac

# 已经是默认分支就跳过;设置失败(如分支不在远端)静默忽略
CURRENT_DEFAULT=$(gh repo view --json defaultBranchRef -q .defaultBranchRef.name 2>/dev/null)
[ "$CURRENT_DEFAULT" = "$BRANCH" ] && exit 0

if gh repo edit --default-branch "$BRANCH" >/dev/null 2>&1; then
  printf '{"systemMessage": "✅ GitHub 默认分支已自动切换为 %s"}\n' "$BRANCH"
fi
exit 0
