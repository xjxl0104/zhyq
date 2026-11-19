package com.zhyq.park.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.contract.entity.RoomRef;

public interface RoomRefMapper extends BaseMapper<RoomRef> {
    @org.apache.ibatis.annotations.Update("""
            UPDATE biz_room r SET r.status = 1
            WHERE r.id = #{roomId} AND r.deleted = 0 AND r.status = 5
              AND NOT EXISTS (SELECT 1 FROM biz_contract_room cr
                  JOIN biz_contract c ON c.id = cr.contract_id AND c.deleted = 0 AND c.status IN (5,8)
                  WHERE cr.room_id = r.id AND cr.deleted = 0 AND c.id <> #{contractId})
            """)
    int releaseIfVacant(@org.apache.ibatis.annotations.Param("roomId") Long roomId,
                        @org.apache.ibatis.annotations.Param("contractId") Long contractId);
}
