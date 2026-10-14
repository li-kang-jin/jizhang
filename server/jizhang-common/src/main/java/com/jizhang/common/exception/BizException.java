package com.jizhang.common.exception;

import com.jizhang.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常。抛出后由 {@link GlobalExceptionHandler} 统一转换为响应体。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException of(ResultCode resultCode) {
        return new BizException(resultCode);
    }

    /** 条件成立时抛出业务异常 */
    public static void throwIf(boolean condition, ResultCode resultCode) {
        if (condition) {
            throw new BizException(resultCode);
        }
    }

    /** 对象为空时抛出业务异常 */
    public static <T> T requireNonNull(T obj, ResultCode resultCode) {
        if (obj == null) {
            throw new BizException(resultCode);
        }
        return obj;
    }
}
