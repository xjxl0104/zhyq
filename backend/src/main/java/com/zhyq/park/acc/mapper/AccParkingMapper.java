package com.zhyq.park.acc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.acc.entity.AccParking;

public interface AccParkingMapper extends BaseMapper<AccParking> {
    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO acc_parking_plate_lock (plate_no) VALUES (#{plate})
            ON DUPLICATE KEY UPDATE plate_no = plate_no
            """)
    void lockPlate(String plate);
}
