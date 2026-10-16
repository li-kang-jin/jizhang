package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.jizhang.dao.entity.RecurringExpenseEntity;
import lombok.Data;

import java.time.LocalDate;

/**
 * 固定支出规则。
 * <p>
 * 只带回规则本身，不带「本期预留了多少」——预留金额依赖客户端的本地时区与本地流水，
 * 由客户端按 {@code RecurringExpenses} 实时计算，放进响应里只会变成一份会过期的快照。
 */
@Data
public class RecurringExpenseVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    /** 0 表示未指定分类 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    /** 频率：1=每周 2=每月 3=每年 */
    private Integer freq;

    /** 每周用：1=周一 … 7=周日 */
    private Integer dayOfWeek;

    /** 每月用：1-31；当月天数不足时取当月最后一天 */
    private Integer dayOfMonth;

    /** 每年用：1-12 */
    private Integer monthOfYear;

    /** 金额（分） */
    private Long amount;

    /** 生效首日（含） */
    private LocalDate startDate;

    /** 失效日（含），null 表示长期有效 */
    private LocalDate endDate;

    private Boolean enabled;

    private String remark;

    public static RecurringExpenseVO from(RecurringExpenseEntity entity) {
        RecurringExpenseVO vo = new RecurringExpenseVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setCategoryId(entity.getCategoryId());
        vo.setFreq(entity.getFreq());
        vo.setDayOfWeek(entity.getDayOfWeek());
        vo.setDayOfMonth(entity.getDayOfMonth());
        vo.setMonthOfYear(entity.getMonthOfYear());
        vo.setAmount(entity.getAmount());
        vo.setStartDate(entity.getStartDate());
        vo.setEndDate(entity.getEndDate());
        vo.setEnabled(entity.getEnabled() == null || entity.getEnabled() != 0);
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
