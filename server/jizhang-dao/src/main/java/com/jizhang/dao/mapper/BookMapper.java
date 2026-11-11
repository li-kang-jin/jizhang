package com.jizhang.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhang.dao.entity.BookEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface BookMapper extends BaseMapper<BookEntity> {

    /** 平台账本总数 */
    @Select("SELECT count(*) FROM t_book WHERE deleted = 0")
    long countAll();

    /** 指定时间之后创建的账本数 */
    @Select("SELECT count(*) FROM t_book WHERE deleted = 0 AND created_at >= #{start}")
    long countSince(@Param("start") OffsetDateTime start);

    /** 同步：查询当前用户已软删除的账本 ID */
    @Select("SELECT id FROM t_book WHERE owner_id = #{userId} AND deleted = 1 AND updated_at > #{since}")
    List<Long> selectDeletedIdsSince(@Param("userId") Long userId, @Param("since") OffsetDateTime since);

    /**
     * 管理后台：按 ID 批量取名称，忽略逻辑删除。
     * <p>
     * 已删除的流水引用的账本往往也已经删掉，走 {@code selectBatchIds}（自动带 deleted = 0）
     * 会查不到名字，后台就只剩一串 ID，失去「这条账单属于谁」的信息。
     */
    @Select("<script>SELECT id, name FROM t_book WHERE id IN "
            + "<foreach collection='ids' item='bid' open='(' separator=',' close=')'>#{bid}</foreach>"
            + "</script>")
    List<BookEntity> selectNamesByIdsIgnoreDeleted(@Param("ids") Collection<Long> ids);
}
