package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 投资成本流水：一笔转账记到了哪个品种上。
 * <p>
 * 这张表在服务端是**按持仓全量覆盖**写入的：客户端推某个持仓时，服务端先物理删掉该持仓的
 * 全部流水，再插入客户端这份。因此不需要「删除标记」—— 客户端删掉一笔流水，只要把持仓
 * 标记为待推，下次推送它就不在列表里，服务端自然跟着消失。
 * <p>
 * {@code transactionId} 上有唯一索引：一笔转账最多只能算到一个品种头上，
 * 重复入库会让成本凭空翻倍。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_invest_flow")
public class InvestFlowEntity extends BaseEntity {

    private Long bookId;

    private Long positionId;

    /** 关联的账单 id（{@code t_transaction.id}） */
    private Long transactionId;

    /** 1=买入（钱转入投资账户） 2=卖出（钱从投资账户转出） */
    private Integer side;

    /** 计入成本的金额（分） */
    private Long amountFen;

    /** 发生时间（epoch millis） */
    private Long occurredAt;
}
