package com.jizhang.service.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 按成员维度的收支统计（家庭账本「谁花了多少」）。
 */
@Data
public class MemberReportVO {

    /** 全部成员收入合计（分） */
    private long totalIncome;

    /** 全部成员支出合计（分） */
    private long totalExpense;

    private List<MemberStatVO> items = new ArrayList<>();
}
