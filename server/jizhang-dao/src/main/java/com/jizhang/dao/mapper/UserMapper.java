package com.jizhang.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhang.dao.dto.DailyStatRow;
import com.jizhang.dao.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    /** 平台用户总数 */
    @Select("SELECT count(*) FROM t_user WHERE deleted = 0")
    long countAll();

    /** 指定时间之后注册的用户数 */
    @Select("SELECT count(*) FROM t_user WHERE deleted = 0 AND created_at >= #{start}")
    long countSince(@Param("start") OffsetDateTime start);

    /** 按日统计新增用户 */
    @Select("SELECT to_char(created_at, 'YYYY-MM-DD') AS day, count(*) AS cnt "
            + "FROM t_user WHERE deleted = 0 AND created_at >= #{start} GROUP BY 1 ORDER BY 1")
    List<DailyStatRow> dailyNewUsers(@Param("start") OffsetDateTime start);

    /**
     * 自增令牌版本，使该用户已签发的 accessToken / refreshToken 立即失效。
     * <p>
     * 用 SQL 自增而不是「读出来 +1 再写回」，避免并发改密时丢更新。
     */
    @Update("UPDATE t_user SET token_version = token_version + 1, updated_at = now() "
            + "WHERE id = #{userId} AND deleted = 0")
    int incrementTokenVersion(@Param("userId") Long userId);
}
