package com.jizhang.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhang.dao.dto.CategoryStatRow;
import com.jizhang.dao.dto.DailyStatRow;
import com.jizhang.dao.dto.MemberStatRow;
import com.jizhang.dao.dto.PeriodStatRow;
import com.jizhang.dao.dto.SumRow;
import com.jizhang.dao.entity.TransactionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 交易流水 Mapper。
 * <p>
 * 自定义 SQL 均需显式带上 {@code deleted = 0}，因为 {@code @TableLogic} 只对
 * MyBatis-Plus 自动生成的语句生效，不作用于手写 SQL。
 */
@Mapper
public interface TransactionMapper extends BaseMapper<TransactionEntity> {

    /* ---------------- 平台运营统计（管理后台） ---------------- */

    /** 平台交易总笔数 */
    @Select("SELECT count(*) FROM t_transaction WHERE deleted = 0")
    long countAll();

    /**
     * 指定时间之后的交易笔数。
     * <p>
     * 按 {@code trade_time} 而不是 {@code created_at}：离线补录会把「昨天花的钱」记在今天，
     * 用创建时间统计会让笔数和金额（按 trade_time 算）互相打架。
     */
    @Select("SELECT count(*) FROM t_transaction WHERE deleted = 0 AND trade_time >= #{start}")
    long countSince(@Param("start") OffsetDateTime start);

    /** 指定类型的累计金额（分），1=支出 2=收入 3=转账 */
    @Select("SELECT COALESCE(sum(amount), 0) FROM t_transaction WHERE deleted = 0 AND type = #{type}")
    long sumAmountByType(@Param("type") int type);

    /** 指定时间之后、指定类型的金额（分） */
    @Select("SELECT COALESCE(sum(amount), 0) FROM t_transaction WHERE deleted = 0 AND type = #{type} "
            + "AND trade_time >= #{start}")
    long sumAmountByTypeSince(@Param("type") int type, @Param("start") OffsetDateTime start);

    /** 指定时间之后有记账行为的账本数（去重），作为平台活跃度的代理指标 */
    @Select("SELECT count(DISTINCT book_id) FROM t_transaction WHERE deleted = 0 AND trade_time >= #{start}")
    long countActiveBooksSince(@Param("start") OffsetDateTime start);

    /** 按日统计交易笔数与收支金额（转账不计入收支） */
    @Select("SELECT to_char(trade_time, 'YYYY-MM-DD') AS day, count(*) AS cnt, "
            + "COALESCE(sum(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense, "
            + "COALESCE(sum(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income "
            + "FROM t_transaction WHERE deleted = 0 AND trade_time >= #{start} GROUP BY 1 ORDER BY 1")
    List<DailyStatRow> dailyTransactions(@Param("start") OffsetDateTime start);

    /* ---------------- 账本报表（C 端） ---------------- */

    /**
     * 指定区间的收入 / 支出合计。
     * <p>
     * 转账（type = 3）不计入收支统计。
     */
    @Select("SELECT COALESCE(sum(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, "
            + "COALESCE(sum(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense "
            + "FROM t_transaction WHERE deleted = 0 AND book_id = #{bookId} "
            + "AND trade_time >= #{start} AND trade_time < #{end}")
    SumRow sumIncomeExpense(@Param("bookId") Long bookId,
                            @Param("start") OffsetDateTime start,
                            @Param("end") OffsetDateTime end);

    /**
     * 分类占比统计。
     *
     * @param type 1=支出 2=收入，传 null 表示不限制
     */
    @Select("<script>"
            + "SELECT t.category_id AS category_id, c.name AS category_name, c.icon, c.color, "
            + "COALESCE(sum(t.amount), 0) AS total_amount "
            + "FROM t_transaction t LEFT JOIN t_category c ON c.id = t.category_id "
            + "WHERE t.deleted = 0 AND t.book_id = #{bookId} "
            + "<if test='type != null'>AND t.type = #{type} </if>"
            + "AND t.trade_time &gt;= #{start} AND t.trade_time &lt; #{end} "
            + "GROUP BY t.category_id, c.name, c.icon, c.color "
            + "ORDER BY total_amount DESC"
            + "</script>")
    List<CategoryStatRow> statByCategory(@Param("bookId") Long bookId,
                                         @Param("type") Integer type,
                                         @Param("start") OffsetDateTime start,
                                         @Param("end") OffsetDateTime end);

    /**
     * 收支趋势统计。
     *
     * @param pattern to_char 的格式串：日粒度 {@code YYYY-MM-DD}，月粒度 {@code YYYY-MM}
     */
    @Select("SELECT to_char(trade_time, #{pattern}) AS period, "
            + "COALESCE(sum(CASE WHEN type = 2 THEN amount ELSE 0 END), 0) AS income, "
            + "COALESCE(sum(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS expense "
            + "FROM t_transaction WHERE deleted = 0 AND book_id = #{bookId} "
            + "AND trade_time >= #{start} AND trade_time < #{end} "
            + "GROUP BY period ORDER BY period")
    List<PeriodStatRow> statByPeriod(@Param("bookId") Long bookId,
                                     @Param("pattern") String pattern,
                                     @Param("start") OffsetDateTime start,
                                     @Param("end") OffsetDateTime end);

    /**
     * 按记账人统计收入 / 支出（家庭账本的核心诉求：谁花了多少）。
     * <p>
     * 转账（type = 3）不计入收支统计。
     */
    @Select("SELECT t.created_by AS user_id, "
            + "COALESCE(sum(CASE WHEN t.type = 2 THEN t.amount ELSE 0 END), 0) AS income, "
            + "COALESCE(sum(CASE WHEN t.type = 1 THEN t.amount ELSE 0 END), 0) AS expense, "
            + "count(*) AS tx_count "
            + "FROM t_transaction t WHERE t.deleted = 0 AND t.book_id = #{bookId} "
            + "AND t.trade_time >= #{start} AND t.trade_time < #{end} "
            + "GROUP BY t.created_by ORDER BY expense DESC")
    List<MemberStatRow> statByMember(@Param("bookId") Long bookId,
                                     @Param("start") OffsetDateTime start,
                                     @Param("end") OffsetDateTime end);

    /* ---------------- 多端同步 ---------------- */

    /**
     * 查询指定账本内已软删除的交易 ID。
     * <p>
     * 同步场景需要把「删除」这个动作同步给客户端，因此必须能查到 deleted = 1 的记录。
     */
    @Select("SELECT id FROM t_transaction WHERE book_id = #{bookId} AND deleted = 1 AND updated_at > #{since}")
    List<Long> selectDeletedIdsSince(@Param("bookId") Long bookId, @Param("since") OffsetDateTime since);
}
