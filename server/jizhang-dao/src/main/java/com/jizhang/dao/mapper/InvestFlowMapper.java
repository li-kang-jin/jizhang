package com.jizhang.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhang.dao.entity.InvestFlowEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 投资成本流水 Mapper。
 * <p>
 * 流水在服务端是「按持仓全量覆盖」的：每次同步先删后插。**必须物理删** ——
 * {@code transaction_id} 上有唯一索引，逻辑删留下的 {@code deleted = 1} 行会让
 * 同一笔转账下次再挂到这个品种时主键冲突。
 */
@Mapper
public interface InvestFlowMapper extends BaseMapper<InvestFlowEntity> {

    @Delete("DELETE FROM t_invest_flow WHERE position_id = #{positionId}")
    int deletePhysicallyByPositionId(@Param("positionId") Long positionId);
}
