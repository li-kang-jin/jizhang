package com.jizhang.common.api;

/**
 * 统一业务状态码。
 */
public enum ResultCode {

    SUCCESS(0, "success"),

    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    ERROR(500, "服务器内部错误"),

    /* 用户 1xxx */
    USERNAME_EXISTS(1001, "用户名已存在"),
    USERNAME_OR_PASSWORD_ERROR(1002, "用户名或密码错误"),
    USER_DISABLED(1003, "账号已被禁用"),
    TOKEN_INVALID(1004, "Token 已失效，请重新登录"),
    USER_NOT_FOUND(1005, "用户不存在"),

    /* 账本 2xxx */
    BOOK_NOT_FOUND(2001, "账本不存在"),
    BOOK_NO_PERMISSION(2002, "无该账本访问权限"),

    /* 账户 3xxx */
    ACCOUNT_NOT_FOUND(3001, "账户不存在"),
    ACCOUNT_BALANCE_NOT_ENOUGH(3002, "账户余额不足"),

    /* 分类 4xxx */
    CATEGORY_NOT_FOUND(4001, "分类不存在"),
    CATEGORY_IN_USE(4002, "分类已被交易引用，无法删除"),

    /* 交易 5xxx */
    TRANSACTION_NOT_FOUND(5001, "交易记录不存在");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
