package com.jizhang.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 交易流水（核心表）。
 * <p>
 * {@code autoResultMap = true} 用于让 jsonb 字段的 TypeHandler 在查询时生效。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_transaction", autoResultMap = true)
public class TransactionEntity extends BaseEntity {

    private Long bookId;

    /** 1=支出 2=收入 3=转账 */
    private Integer type;

    /** 金额（分），恒为正数，方向由 type 决定 */
    private Long amount;

    /** 账户；转账时为转出账户 */
    private Long accountId;

    /** 转入账户，仅 type=3 使用 */
    private Long toAccountId;

    /** 分类，转账时为空 */
    private Long categoryId;

    /** 交易发生时间（用户可修改） */
    private OffsetDateTime tradeTime;

    private String remark;

    /** 票据图片 URL 列表 */
    @TableField(value = "images", typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /** 标签 */
    @TableField(value = "tags", typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private BigDecimal longitude;

    private BigDecimal latitude;

    /** 来源：1=手动 2=导入 3=周期自动 */
    private Integer source;

    private Integer version;
}
