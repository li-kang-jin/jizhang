package com.jizhang.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhang.dao.entity.RecurringExpenseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 固定支出 Mapper。
 * <p>
 * 手写 SQL 需显式带上 {@code deleted = 0}，{@code @TableLogic} 不作用于自定义语句。
 */
@Mapper
public interface RecurringExpenseMapper extends BaseMapper<RecurringExpenseEntity> {

    /** 查询某账本已软删除的固定支出 ID（同步场景要把「删除」动作下发给客户端，必须能查到 deleted = 1 的行） */
    @Select("SELECT id FROM t_recurring_expense WHERE book_id = #{bookId} AND deleted = 1 "
            + "AND updated_at > #{since}")
    List<Long> selectDeletedIdsSince(@Param("bookId") Long bookId, @Param("since") OffsetDateTime since);
}
