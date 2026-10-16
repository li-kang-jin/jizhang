package com.jizhang.service;

import com.jizhang.service.dto.RecurringExpenseSaveRequest;
import com.jizhang.service.vo.RecurringExpenseVO;

import java.util.List;

/**
 * 固定支出服务。
 * <p>
 * 固定支出只登记规则，不生成交易、不影响账本余额与报表；它存在的意义是让客户端把
 * 「本期还没到期的部分」从预算的每日可花额度里预留出来。
 */
public interface RecurringExpenseService {

    /** 某账本的全部固定支出（含已停用） */
    List<RecurringExpenseVO> list(Long bookId);

    /** 保存固定支出（同 id 视为修改），返回固定支出 ID */
    Long save(RecurringExpenseSaveRequest request);

    /** 删除固定支出（软删除，删除动作会通过同步下发给其它设备） */
    void delete(Long bookId, Long id);
}
