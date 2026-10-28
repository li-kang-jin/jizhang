package com.jizhang.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 投资持仓（跨设备同步用）。
 * <p>
 * 三个时间字段一律用 **epoch millis**，而不是 ISO 字符串：估值时点与成本发生时点都是
 * 客户端本地产生的瞬间，转成带时区的字符串再解析回来，跨时区会漂。
 */
@Serializable
data class InvestPositionDto(
    val id: Long,
    /** 响应里可能不带：客户端合并时一律用请求的 bookId，不信任服务端回传的值 */
    val bookId: Long = 0L,
    val accountId: Long,
    val name: String,
    val code: String? = null,
    /** 1=股票 2=基金 3=债券 4=理财 5=其他 */
    val kind: Int = 2,
    /** 期初成本（分）：转账自动累计的部分不在这里，由 flow 表达 */
    val costFen: Long = 0,
    val sharesScaled: Long? = null,
    val targetPercent: Int = 0,
    val sort: Int = 0,
    val note: String? = null,
    val archived: Boolean = false
)

/** 估值快照：只增不改，每条记录一次「更新市值」 */
@Serializable
data class InvestValuationDto(
    val id: Long,
    val bookId: Long = 0L,
    val positionId: Long,
    val valueFen: Long,
    /** 估值时点（epoch millis） */
    val valuedAt: Long
)

/** 成本流水：哪笔转账记到了哪个品种上 */
@Serializable
data class InvestFlowDto(
    val id: Long,
    val bookId: Long = 0L,
    val positionId: Long,
    val transactionId: Long,
    /** 1=买入 2=卖出 */
    val side: Int,
    val amountFen: Long,
    /** 发生时间（epoch millis） */
    val occurredAt: Long
)

/**
 * 投资数据的批量同步请求。
 * <p>
 * 一个账本的全部投资数据一次提交，而不是每种资源一套增删改接口：这三张表是一体的
 * （持仓没了，它的估值和流水也没有意义），分批推会出现「推了流水但持仓还没到」的中间态，
 * 服务端得为它做额外的一致性处理。
 */
@Serializable
data class InvestSyncRequest(
    val bookId: Long,
    val positions: List<InvestPositionDto> = emptyList(),
    val valuations: List<InvestValuationDto> = emptyList(),
    val flows: List<InvestFlowDto> = emptyList(),
    val deletedPositionIds: List<Long> = emptyList(),
    val deletedValuationIds: List<Long> = emptyList(),
    val deletedFlowIds: List<Long> = emptyList()
) {
    val isEmpty: Boolean
        get() = positions.isEmpty() && valuations.isEmpty() && flows.isEmpty() &&
            deletedPositionIds.isEmpty() && deletedValuationIds.isEmpty() && deletedFlowIds.isEmpty()
}

/** 拉取到的投资数据（服务端全量返回，客户端按 id 合并） */
@Serializable
data class InvestDataDto(
    val positions: List<InvestPositionDto> = emptyList(),
    val valuations: List<InvestValuationDto> = emptyList(),
    val flows: List<InvestFlowDto> = emptyList()
) {
    val isEmpty: Boolean
        get() = positions.isEmpty() && valuations.isEmpty() && flows.isEmpty()
}
