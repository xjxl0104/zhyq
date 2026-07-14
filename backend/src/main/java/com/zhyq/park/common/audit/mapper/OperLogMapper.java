package com.zhyq.park.common.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.common.audit.OperLog;
import org.apache.ibatis.annotations.Mapper;

/** 操作审计日志 Mapper。放在 *.mapper 包以匹配主类 @MapperScan("com.zhyq.park.**.mapper")。 */
@Mapper
public interface OperLogMapper extends BaseMapper<OperLog> {
}
