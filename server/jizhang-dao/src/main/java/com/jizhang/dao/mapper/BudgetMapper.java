package com.jizhang.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhang.dao.entity.BudgetEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 预算 Mapper。
 * <p>
 * 手写 SQL 需显式带上 {@code deleted = 0}，{@code @TableLogic} 不作用于自定义语句。
 */
@Mapper
public interface BudgetMapper extends BaseMapper<BudgetEntity> {

    /** 查询某账本某周期的全部预算（总预算的 category_id 为 0，排在最前） */
    @Select("SELECT * FROM t_budget WHERE deleted = 0 AND book_id = #{bookId} AND period = #{period} "
            + "ORDER BY category_id")
    List<BudgetEntity> selectByPeriod(@Param("bookId") Long bookId, @Param("period") String period);

    /**
     * 查询指定账本内已软删除的预算 ID。
     * <p>
     * 同步场景需要把「删除」动作同步给客户端，因此必须能查到 deleted = 1 的记录。
     */
    @Select("SELECT id FROM t_budget WHERE book_id = #{bookId} AND deleted = 1 AND updated_at > #{since}")
    List<Long> selectDeletedIdsSince(@Param("bookId") Long bookId, @Param("since") OffsetDateTime since);
}
