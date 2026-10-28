package com.jizhang.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhang.dao.entity.InvestValuationEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 投资估值 Mapper。
 * <p>
 * 估值只增不改，但持仓被删除时它的估值也没有意义了，所以提供一个**物理删**：
 * 走逻辑删会留下 {@code deleted = 1} 的行，白白占存储（这些行永远不会被读到）。
 */
@Mapper
public interface InvestValuationMapper extends BaseMapper<InvestValuationEntity> {

    @Delete("DELETE FROM t_invest_valuation WHERE position_id = #{positionId}")
    int deletePhysicallyByPositionId(@Param("positionId") Long positionId);
}
