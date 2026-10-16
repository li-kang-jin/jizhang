package com.jizhang.service.vo;

import com.jizhang.dao.entity.BudgetEntity;
import com.jizhang.service.support.BudgetPeriod;
import lombok.Data;

import java.time.LocalDate;

/**
 * 单条预算及其执行进度。
 */
@Data
public class BudgetVO {

    private Long id;

    /** 0 表示账本总预算 */
    private Long categoryId;

    /** 分类名称，总预算时为「总预算」 */
    private String categoryName;

    private String categoryIcon;

    /** 周期类型：1=每月 2=每周 3=每年 */
    private Integer cycle;

    /** 周期首日（含） */
    private LocalDate periodStart;

    /** 周期字符串（2026-10 / 2026-W42 / 2026），便于展示与排查 */
    private String period;

    /** 预算金额（分） */
    private Long amount;

    /** 已用金额（分） */
    private Long used;

    /** 剩余金额（分），超支时为负 */
    private Long remaining;

    /** 使用比例，可能大于 1（超支） */
    private Double usedRatio;

    /** 是否已超支 */
    private Boolean exceeded;

    private String remark;

    /**
     * 同步拉取用：只带回额度本身，不带进度。
     * <p>
     * 进度（used / remaining / exceeded）每次由客户端按本地流水实时聚合，
     * 放进同步数据里只会变成一份会过期的快照。
     */
    public static BudgetVO from(BudgetEntity entity) {
        BudgetVO vo = new BudgetVO();
        vo.setId(entity.getId());
        vo.setCategoryId(entity.getCategoryId());
        vo.setCycle(entity.getCycle());
        vo.setPeriodStart(entity.getPeriodStart());
        vo.setPeriod(entity.getCycle() == null || entity.getPeriodStart() == null
                ? null
                : BudgetPeriod.of(entity.getCycle(), entity.getPeriodStart()).label());
        vo.setAmount(entity.getAmount());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
