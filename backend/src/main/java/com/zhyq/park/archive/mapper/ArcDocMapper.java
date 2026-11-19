package com.zhyq.park.archive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.archive.entity.ArcDoc;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ArcDocMapper extends BaseMapper<ArcDoc> {

    /** 同项目、同前缀下已用过的最大档号(含已删除的,避免档号被复用) */
    @Select("SELECT MAX(archive_no) FROM arc_doc WHERE archive_no LIKE CONCAT(#{prefix}, '%') "
        + "AND IFNULL(project_id, 0) = IFNULL(#{projectId}, 0)")
    String maxArchiveNo(@Param("prefix") String prefix, @Param("projectId") Long projectId);

    /** 文件夹模式下全局资料编号的最大值(格式 ZL-000001,含已删除的,避免编号复用) */
    @Select("SELECT MAX(archive_no) FROM arc_doc WHERE archive_no LIKE 'ZL-%'")
    String maxFolderDocNo();
}
