package com.jizhang.common.constant;

/**
 * 全局常量。
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** 认证请求头 */
    public static final String TOKEN_HEADER = "Authorization";

    /** Token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 设备标识请求头，用于多端同步 */
    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    /** 链路追踪 MDC key */
    public static final String TRACE_ID = "traceId";

    /** 接口版本前缀 */
    public static final String API_PREFIX = "/api/v1";

    /** 角色 */
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    /** 通用状态 */
    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;

    /** 软删除标记 */
    public static final int NOT_DELETED = 0;
    public static final int DELETED = 1;
}
