package com.zhyq.park.oa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤结果(oa_attendance,规格书:首期只同步不自研排班)
 * 状态:正常/迟到/早退/缺勤/外勤
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_attendance")
public class Attendance extends BaseEntity {

    /** 姓名 */
    private String userName;
    /** 考勤日期 */
    private LocalDate attDate;
    /** 签到时间 */
    private LocalDateTime checkin;
    /** 签退时间 */
    private LocalDateTime checkout;
    /** 考勤状态:正常/迟到/早退/缺勤/外勤 */
    private String attStatus;
    /** 备注 */
    private String remark;
}
