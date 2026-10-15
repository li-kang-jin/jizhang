package com.jizhang.service.vo;

import lombok.Data;

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
}
