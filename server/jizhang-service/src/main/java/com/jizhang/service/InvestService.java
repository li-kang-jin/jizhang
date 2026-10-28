package com.jizhang.service;

import com.jizhang.service.dto.InvestSyncRequest;
import com.jizhang.service.vo.InvestDataVO;

/**
 * 投资持仓服务。
 * <p>
 * 与预算 / 固定支出同一套离线优先约定：客户端先写本地并标记待同步，再走 REST 推送；
 * 拉取时本地未推送的改动优先，不被服务端旧值覆盖。
 */
public interface InvestService {

    /** 拉取某账本的全部投资数据（持仓 / 估值 / 成本流水） */
    InvestDataVO load(Long bookId);

    /** 批量写入：按客户端 id 幂等 upsert，流水按持仓全量覆盖 */
    void sync(InvestSyncRequest request);
}
