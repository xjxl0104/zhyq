package com.zhyq.park.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果封装
 */
@Data
public class PageResult<T> implements Serializable {

    private long total;
    private List<T> records;

    public static <T> PageResult<T> of(long total, List<T> records) {
        PageResult<T> pr = new PageResult<>();
        pr.setTotal(total);
        pr.setRecords(records == null ? Collections.emptyList() : records);
        return pr;
    }
}
