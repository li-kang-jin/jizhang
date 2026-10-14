package com.jizhang.service;

import com.jizhang.service.dto.CategorySaveRequest;
import com.jizhang.service.vo.CategoryVO;

import java.util.List;

/**
 * 分类服务。
 */
public interface CategoryService {

    /**
     * 树形分类列表。
     *
     * @param type 1=支出 2=收入，为 null 返回全部
     */
    List<CategoryVO> tree(Long bookId, Integer type);

    CategoryVO create(CategorySaveRequest request);

    CategoryVO update(Long categoryId, CategorySaveRequest request);

    /** 删除分类；已被交易引用时抛出业务异常 */
    void delete(Long categoryId);
}
