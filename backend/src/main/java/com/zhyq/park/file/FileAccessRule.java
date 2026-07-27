package com.zhyq.park.file;

import com.zhyq.park.file.entity.SysFile;

/**
 * 附件删除规则(纯函数,可单测):
 * 仅 上传者本人 或 admin 角色 可删,防任意登录用户按 id 遍历删除他人附件(IDOR)。
 */
public final class FileAccessRule {

    private FileAccessRule() {}

    /**
     * @param file     附件记录(createBy = 上传者用户名)
     * @param username 当前登录用户名
     * @param isAdmin  是否 admin 角色(ROLE_admin)
     */
    public static boolean canDelete(SysFile file, String username, boolean isAdmin) {
        if (file == null) return false;
        if (isAdmin) return true;
        return username != null && !username.isBlank() && username.equals(file.getCreateBy());
    }
}
