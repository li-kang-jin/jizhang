package com.jizhang.api.controller;

import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.CategoryService;
import com.jizhang.service.dto.CategorySaveRequest;
import com.jizhang.service.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类接口。
 */
@Tag(name = "分类", description = "收支分类（树形）")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/app/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "分类树")
    @GetMapping
    public Result<List<CategoryVO>> tree(@RequestParam Long bookId,
                                         @RequestParam(required = false) Integer type) {
        return Result.success(categoryService.tree(bookId, type));
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public Result<CategoryVO> create(@Valid @RequestBody CategorySaveRequest request) {
        return Result.success(categoryService.create(request));
    }

    @Operation(summary = "修改分类")
    @PutMapping("/{id}")
    public Result<CategoryVO> update(@PathVariable Long id, @Valid @RequestBody CategorySaveRequest request) {
        return Result.success(categoryService.update(id, request));
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }
}
