package com.jizhang.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhang.dao.entity.InvestPositionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 投资持仓 Mapper。
 * <p>
 * 没有自定义 SQL：查询与逻辑删除都用 MyBatis-Plus 的默认实现
 * （{@code @TableLogic} 会自动带上 {@code deleted = 0}）。
 */
@Mapper
public interface InvestPositionMapper extends BaseMapper<InvestPositionEntity> {
}
