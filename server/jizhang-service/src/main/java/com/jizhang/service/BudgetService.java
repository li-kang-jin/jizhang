package com.jizhang.service;

import com.jizhang.service.dto.BudgetSaveRequest;
import com.jizhang.service.support.BudgetPeriod;
import com.jizhang.service.vo.BudgetOverviewVO;
import com.jizhang.service.vo.BudgetVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 预算服务。预算只存额度，进度按交易流水实时聚合。
 */
public interface BudgetService {

    /** 某周期的分类预算列表（含执行进度） */
    List<BudgetVO> list(Long bookId, BudgetPeriod period);

    /**
     * 预算总览：总预算执行情况 + 分类明细 + 超支提醒 + 每日预测。
     *
     * @param today 「今天」，用于算剩余天数与预测。由调用方显式传入，
     *              避免服务端用 UTC 的今天、客户端用本地时区的今天，两边算出的
     *              「每天可花多少」差一天。
     */
    BudgetOverviewVO overview(Long bookId, BudgetPeriod period, LocalDate today);

    /** 保存预算（同账本 + 分类 + 周期视为更新），返回预算 ID */
    Long save(BudgetSaveRequest request);

    /** 删除预算 */
    void delete(Long bookId, Long id);
}
