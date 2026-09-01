---
description: 一键拉取远端最新代码并快进当前分支(等价于开会话时的自动同步)
allowed-tools: Bash(git *), Bash(bash *)
---

执行 `bash "$CLAUDE_PROJECT_DIR/.claude/hooks/sync-on-start.sh"` 完成同步(git fetch origin;工作区干净且未分叉时把当前分支快进到远端最新)。

然后简要汇报:
1. 当前分支和最新提交;
2. test 与 main 之间是否有落差(`git rev-list --count origin/main..origin/test` 及反向);
3. 若脚本报"未自动合并"(有本地改动或分叉),列出具体差异并等我决定怎么处理,不要擅自 merge/rebase/stash。
