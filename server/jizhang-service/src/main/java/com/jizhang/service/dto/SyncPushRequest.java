package com.jizhang.service.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量推送请求（客户端离线期间产生的变更）。
 */
@Data
public class SyncPushRequest {

    @NotBlank(message = "设备标识不能为空")
    private String deviceId;

    @NotNull(message = "账本不能为空")
    private Long bookId;

    /** 新增或修改的交易 */
    @Valid
    private List<TransactionSaveRequest> transactions = new ArrayList<>();

    /**
     * 客户端在离线期间删除的交易 ID。
     * <p>
     * 删除必须走这个字段而不是直接删本地：否则断网时服务端数据仍在，
     * 下次拉取会把记录重新带回来（「删了又回来」）。
     */
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> deletedTransactionIds = new ArrayList<>();
}
