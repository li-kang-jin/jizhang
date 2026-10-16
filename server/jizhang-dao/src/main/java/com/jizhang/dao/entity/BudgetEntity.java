package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 预算（可针对整个账本或某个分类，支持每月 / 每周 / 每年）。
 * <p>
 * 周期用「类型 + 首日」表达而不是字符串，避免解析格式与 ISO 周跨年的歧义；
 * 结束日由 cycle 推导（首日 + 1 月 / 1 周 / 1 年），不单独存列，避免两处不一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_budget")
public class BudgetEntity extends BaseEntity {

    private Long bookId;

    /** 0 表示账本总预算，其余为对应分类的预算 */
    private Long categoryId;

    /** 周期类型：1=每月 2=每周 3=每年 */
    private Integer cycle;

    /** 周期首日（含），即左闭右开区间的起点 */
    private LocalDate periodStart;

    /** 预算金额（分） */
    private Long amount;

    private String remark;

    private Integer version;
}
