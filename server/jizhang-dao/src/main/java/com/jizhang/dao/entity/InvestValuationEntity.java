package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 投资估值快照：某个持仓在某个时点的市值。
 * <p>
 * **只增不改**，每次「更新市值」由客户端追加一条。存快照而不是只存当前值，
 * 是为了让净值曲线能回填历史、也能看出「多久没更新了」。
 * <p>
 * {@code valuedAt} 用 **epoch millis** 而不是 TIMESTAMPTZ：这是客户端本地产生的瞬间，
 * 转成带时区的字符串再解析回来，跨时区会漂；而它只用于排序与展示「几天前」，
 * 不需要按时区做任何解释。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_invest_valuation")
public class InvestValuationEntity extends BaseEntity {

    private Long bookId;

    private Long positionId;

    /** 该时点的市值（分） */
    private Long valueFen;

    /** 估值时点（epoch millis） */
    private Long valuedAt;
}
