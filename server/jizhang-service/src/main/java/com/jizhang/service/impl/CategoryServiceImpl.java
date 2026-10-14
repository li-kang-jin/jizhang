package com.jizhang.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jizhang.common.api.ResultCode;
import com.jizhang.common.exception.BizException;
import com.jizhang.dao.entity.CategoryEntity;
import com.jizhang.dao.entity.TransactionEntity;
import com.jizhang.dao.mapper.CategoryMapper;
import com.jizhang.dao.mapper.TransactionMapper;
import com.jizhang.service.CategoryService;
import com.jizhang.service.dto.CategorySaveRequest;
import com.jizhang.service.support.BookAccessChecker;
import com.jizhang.service.vo.CategoryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;
    private final BookAccessChecker bookAccessChecker;

    public CategoryServiceImpl(CategoryMapper categoryMapper,
                               TransactionMapper transactionMapper,
                               BookAccessChecker bookAccessChecker) {
        this.categoryMapper = categoryMapper;
        this.transactionMapper = transactionMapper;
        this.bookAccessChecker = bookAccessChecker;
    }

    @Override
    public List<CategoryVO> tree(Long bookId, Integer type) {
        bookAccessChecker.requireReadable(bookId);

        List<CategoryEntity> list = categoryMapper.selectList(Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getBookId, bookId)
                .eq(type != null, CategoryEntity::getType, type)
                .orderByAsc(CategoryEntity::getSort)
                .orderByAsc(CategoryEntity::getId));

        Map<Long, CategoryVO> voMap = new LinkedHashMap<>();
        list.forEach(entity -> voMap.put(entity.getId(), CategoryVO.from(entity)));

        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryEntity entity : list) {
            CategoryVO vo = voMap.get(entity.getId());
            Long parentId = entity.getParentId();
            boolean isRoot = parentId == null || parentId == 0L || !voMap.containsKey(parentId);
            if (isRoot) {
                roots.add(vo);
            } else {
                voMap.get(parentId).getChildren().add(vo);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryVO create(CategorySaveRequest request) {
        bookAccessChecker.requireWritable(request.getBookId());

        CategoryEntity entity = new CategoryEntity();
        entity.setBookId(request.getBookId());
        entity.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        entity.setName(request.getName());
        entity.setType(request.getType());
        entity.setIcon(request.getIcon());
        entity.setColor(request.getColor());
        entity.setSort(request.getSort() == null ? 0 : request.getSort());
        entity.setVersion(0);
        categoryMapper.insert(entity);
        return CategoryVO.from(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryVO update(Long categoryId, CategorySaveRequest request) {
        CategoryEntity entity = requireCategory(categoryId);
        bookAccessChecker.requireWritable(entity.getBookId());

        entity.setName(request.getName());
        entity.setType(request.getType());
        entity.setIcon(request.getIcon());
        entity.setColor(request.getColor());
        if (request.getSort() != null) {
            entity.setSort(request.getSort());
        }
        if (request.getParentId() != null) {
            entity.setParentId(request.getParentId());
        }
        categoryMapper.updateById(entity);
        return CategoryVO.from(categoryMapper.selectById(categoryId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long categoryId) {
        CategoryEntity entity = requireCategory(categoryId);
        bookAccessChecker.requireWritable(entity.getBookId());

        Long used = transactionMapper.selectCount(Wrappers.<TransactionEntity>lambdaQuery()
                .eq(TransactionEntity::getCategoryId, categoryId));
        if (used != null && used > 0) {
            throw new BizException(ResultCode.CATEGORY_IN_USE);
        }
        Long children = categoryMapper.selectCount(Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getParentId, categoryId));
        if (children != null && children > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "请先删除子分类");
        }
        categoryMapper.deleteById(categoryId);
    }

    private CategoryEntity requireCategory(Long categoryId) {
        CategoryEntity entity = categoryId == null ? null : categoryMapper.selectById(categoryId);
        if (entity == null) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND);
        }
        return entity;
    }
}
