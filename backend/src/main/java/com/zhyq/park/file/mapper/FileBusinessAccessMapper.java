package com.zhyq.park.file.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface FileBusinessAccessMapper {
    /** A feedback manager may only read private uploads genuinely submitted by their original uploader. */
    @Select("""
            SELECT COUNT(*) FROM suggestion_image i
            JOIN suggestion s ON s.id=i.suggestion_id AND s.deleted=0
            JOIN sys_file f ON f.id=i.file_id AND f.deleted=0
            JOIN sys_user u ON u.id=s.user_id AND u.deleted=0
            WHERE f.id=#{fileId} AND u.username=f.create_by
            """)
    int countOwnedSuggestionLinks(@Param("fileId") Long fileId);
}
