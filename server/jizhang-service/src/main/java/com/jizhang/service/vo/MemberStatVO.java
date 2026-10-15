package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 家庭账本中单个成员的收支统计。
 */
@Data
public class MemberStatVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    /** 家庭内称呼 */
    private String alias;

    /** 展示名：优先 alias，其次 nickname，最后 username */
    private String displayName;

    /** 收入合计（分） */
    private long income;

    /** 支出合计（分） */
    private long expense;

    /** 结余 = 收入 - 支出 */
    private long balance;

    /** 记账笔数 */
    private long count;
}
