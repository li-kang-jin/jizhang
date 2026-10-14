package com.jizhang.common.api;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应体。
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> records = Collections.emptyList();

    private long total;

    private long page = 1;

    private long size = 20;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records == null ? Collections.emptyList() : records);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        return result;
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return of(Collections.emptyList(), 0, page, size);
    }
}
