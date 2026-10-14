package com.jizhang.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhang.dao.entity.CategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {

    /** 同步：查询已软删除的分类 ID */
    @Select("SELECT id FROM t_category WHERE book_id = #{bookId} AND deleted = 1 AND updated_at > #{since}")
    List<Long> selectDeletedIdsSince(@Param("bookId") Long bookId, @Param("since") OffsetDateTime since);
}
