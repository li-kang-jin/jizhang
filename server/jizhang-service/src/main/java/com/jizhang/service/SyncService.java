package com.jizhang.service;

import com.jizhang.service.dto.SyncPullRequest;
import com.jizhang.service.dto.SyncPushRequest;
import com.jizhang.service.vo.SyncPullVO;
import com.jizhang.service.vo.SyncPushVO;

/**
 * 多端同步服务。
 * <p>
 * 设计要点：
 * <ol>
 *   <li>增量：客户端带上 lastSyncAt，服务端只返回该时间之后变更的数据；</li>
 *   <li>幂等：交易 ID 由客户端生成，重复推送不会产生脏数据；</li>
 *   <li>删除同步：软删除的记录 ID 单独返回，客户端据此清理本地数据；</li>
 *   <li>冲突：V1 采用 last-write-wins，服务端以 updated_at 较新者为准。</li>
 * </ol>
 */
public interface SyncService {

    /** 增量拉取服务端变更 */
    SyncPullVO pull(SyncPullRequest request);

    /** 批量推送客户端离线变更 */
    SyncPushVO push(SyncPushRequest request);
}
