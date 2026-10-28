package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 投资持仓（一只基金 / 一只股票 / 一笔理财）。
 * <p>
 * 主键由客户端生成，服务端按 id 幂等写入 —— 与 {@link TransactionEntity}、
 * {@link RecurringExpenseEntity} 同一套约定。没有可用的业务键：同名品种允许存在多条
 * （不同券商的同名基金是两笔仓位），所以幂等只能落在客户端 id 上。
 * <p>
 * {@code costFen} 是**期初成本**，不含转账自动累计的部分 —— 后者由 {@link InvestFlowEntity}
 * 表达。客户端展示的「总成本」是两者之和，服务端不重复计算，避免两边口径不一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_invest_position")
public class InvestPositionEntity extends BaseEntity {

    private Long bookId;

    /** 挂在哪个投资账户下（{@code t_account.type = 6}） */
    private Long accountId;

    private String name;

    /** 基金 / 股票代码，可空 */
    private String code;

    /** 1=股票 2=基金 3=债券 4=理财 5=其他 */
    private Integer kind;

    /** 期初成本（分） */
    private Long costFen;

    /** 份额 × 10000，可空 */
    private Long sharesScaled;

    /** 目标占比（%），0 表示未设 */
    private Integer targetPercent;

    /** 列表顺序 */
    private Integer sort;

    private String note;

    /** 1=已归档 0=正常 */
    private Integer archived;
}
