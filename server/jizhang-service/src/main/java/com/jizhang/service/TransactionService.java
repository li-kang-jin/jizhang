package com.jizhang.service;

import com.jizhang.common.api.PageResult;
import com.jizhang.service.dto.TransactionQueryRequest;
import com.jizhang.service.dto.TransactionSaveRequest;
import com.jizhang.service.vo.TransactionVO;

import java.util.List;

/**
 * 交易流水服务。所有写操作都会同步维护关联账户余额。
 */
public interface TransactionService {

    PageResult<TransactionVO> page(TransactionQueryRequest request);

    /** 新增交易，支持客户端传入 id 实现幂等写入 */
    TransactionVO create(TransactionSaveRequest request);

    TransactionVO detail(Long transactionId);

    TransactionVO update(Long transactionId, TransactionSaveRequest request);

    void delete(Long transactionId);

    /** 批量新增（导入、多端同步用） */
    List<TransactionVO> batchCreate(List<TransactionSaveRequest> requests);

    /** 批量删除（多端同步用），会逐条回滚对应的账户余额 */
    void batchDelete(List<Long> transactionIds);
}
