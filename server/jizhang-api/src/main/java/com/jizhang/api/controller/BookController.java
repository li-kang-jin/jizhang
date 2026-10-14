package com.jizhang.api.controller;

import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.BookService;
import com.jizhang.service.dto.BookSaveRequest;
import com.jizhang.service.dto.MemberAddRequest;
import com.jizhang.service.vo.BookMemberVO;
import com.jizhang.service.vo.BookVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账本接口。
 */
@Tag(name = "账本", description = "账本增删改查与成员管理")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/app/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @Operation(summary = "我的账本列表")
    @GetMapping
    public Result<List<BookVO>> list() {
        return Result.success(bookService.listMyBooks());
    }

    @Operation(summary = "创建账本")
    @PostMapping
    public Result<BookVO> create(@Valid @RequestBody BookSaveRequest request) {
        return Result.success(bookService.create(request));
    }

    @Operation(summary = "账本详情")
    @GetMapping("/{id}")
    public Result<BookVO> detail(@PathVariable Long id) {
        return Result.success(bookService.detail(id));
    }

    @Operation(summary = "修改账本")
    @PutMapping("/{id}")
    public Result<BookVO> update(@PathVariable Long id, @Valid @RequestBody BookSaveRequest request) {
        return Result.success(bookService.update(id, request));
    }

    @Operation(summary = "删除账本")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return Result.success();
    }

    @Operation(summary = "成员列表")
    @GetMapping("/{id}/members")
    public Result<List<BookMemberVO>> members(@PathVariable Long id) {
        return Result.success(bookService.members(id));
    }

    @Operation(summary = "添加成员")
    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable Long id, @Valid @RequestBody MemberAddRequest request) {
        bookService.addMember(id, request);
        return Result.success();
    }

    @Operation(summary = "移除成员")
    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        bookService.removeMember(id, userId);
        return Result.success();
    }
}
