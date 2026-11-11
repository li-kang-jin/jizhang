package com.jizhang.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhang.dao.entity.AccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface AccountMapper extends BaseMapper<AccountEntity> {

    /**
     * 原子增减账户余额，避免并发下的读改写丢失。
     *
     * @param delta 变动金额（分），正数为增加，负数为减少
     */
    @Update("UPDATE t_account SET balance = balance + #{delta}, updated_at = now() "
            + "WHERE id = #{accountId} AND deleted = 0")
    int addBalance(@Param("accountId") Long accountId, @Param("delta") long delta);

    /** 同步：查询已软删除的账户 ID */
    @Select("SELECT id FROM t_account WHERE book_id = #{bookId} AND deleted = 1 AND updated_at > #{since}")
    List<Long> selectDeletedIdsSince(@Param("bookId") Long bookId, @Param("since") OffsetDateTime since);

    /** 管理后台：按 ID 批量取名称，忽略逻辑删除（已删除流水引用的账户也要能显示名字） */
    @Select("<script>SELECT id, name FROM t_account WHERE id IN "
            + "<foreach collection='ids' item='aid' open='(' separator=',' close=')'>#{aid}</foreach>"
            + "</script>")
    List<AccountEntity> selectNamesByIdsIgnoreDeleted(@Param("ids") Collection<Long> ids);
}
