package com.zhyq.park.common.base;

import lombok.Data;

/**
 * 分页查询基类
 */
@Data
public class PageQuery {
    private Integer pageNo = 1;
    private Integer pageSize = 10;

    public long offset() {
        return (long) (Math.max(pageNo, 1) - 1) * pageSize;
    }
}
