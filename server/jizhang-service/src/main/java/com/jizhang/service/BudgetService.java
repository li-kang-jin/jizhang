package com.jizhang.service;

import com.jizhang.service.dto.BudgetSaveRequest;
import com.jizhang.service.vo.BudgetOverviewVO;
import com.jizhang.service.vo.BudgetVO;

import java.util.List;

/**
 * 预算服务。预算只存额度，进度按交易流水实时聚合。
 */
public interface BudgetService {

    /**
     * 某周期的分类预算列表（含执行进度）。
     *
     * @param period 格式 yyyy-MM，为空时取当前月
     */
    List<BudgetVO> list(Long bookId, String period);

    /** 预算总览：总预算执行情况 + 分类明细 + 超支提醒 */
    BudgetOverviewVO overview(Long bookId, String period);

    /** 保存预算（同账本 + 分类 + 周期视为更新），返回预算 ID */
    Long save(BudgetSaveRequest request);

    /** 删除预算 */
    void delete(Long bookId, Long id);
}
