package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 固定支出（房租 / 房贷 / 会员费这类按期必须支出的钱）。
 * <p>
 * 只登记规则，**不自动生成账单**：它不产生交易，只参与客户端的预算预留计算 ——
 * 把本期还没到期的金额从「每天可花」里扣掉。
 * <p>
 * 主键由客户端生成（毫秒时间戳左移 20 位 + 20 位随机数，见客户端 {@code newClientId()}），
 * 服务端按 id 幂等写入，离线创建的多端记录不会互相覆盖。没有可用作业务键的字段
 * （同名固定支出允许存在多条），所以幂等只能靠客户端 id，这与 {@link TransactionEntity} 一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_recurring_expense")
public class RecurringExpenseEntity extends BaseEntity {

    private Long bookId;

    private String name;

    /** 归类到的分类，0 表示未指定 */
    private Long categoryId;

    /** 频率：1=每周 2=每月 3=每年 */
    private Integer freq;

    /** 每周用：1=周一 … 7=周日 */
    private Integer dayOfWeek;

    /** 每月用：1-31；当月天数不足时取当月最后一天（例如 31 号在小月按 30 号） */
    private Integer dayOfMonth;

    /** 每年用：1-12 */
    private Integer monthOfYear;

    /** 金额（分） */
    private Long amount;

    /** 生效首日（含） */
    private LocalDate startDate;

    /** 失效日（含），null 表示长期有效 */
    private LocalDate endDate;

    /** 1=启用 0=停用，停用的不参与预算预留 */
    private Integer enabled;

    private String remark;

    private Integer version;
}
