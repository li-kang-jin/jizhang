package com.jizhang.api.controller;

import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.SyncService;
import com.jizhang.service.dto.SyncPullRequest;
import com.jizhang.service.dto.SyncPushRequest;
import com.jizhang.service.vo.SyncPullVO;
import com.jizhang.service.vo.SyncPushVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多端同步接口（C 端离线记账使用）。
 */
@Tag(name = "同步", description = "增量拉取与批量推送")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/app/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @Operation(summary = "增量拉取", description = "首次同步不传 lastSyncAt 即返回全量")
    @PostMapping("/pull")
    public Result<SyncPullVO> pull(@Valid @RequestBody SyncPullRequest request) {
        return Result.success(syncService.pull(request));
    }

    @Operation(summary = "批量推送", description = "推送客户端离线期间产生的变更，返回成功与冲突列表")
    @PostMapping("/push")
    public Result<SyncPushVO> push(@Valid @RequestBody SyncPushRequest request) {
        return Result.success(syncService.push(request));
    }
}
