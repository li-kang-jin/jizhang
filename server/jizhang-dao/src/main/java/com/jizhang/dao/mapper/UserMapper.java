package com.jizhang.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhang.dao.dto.DailyStatRow;
import com.jizhang.dao.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
