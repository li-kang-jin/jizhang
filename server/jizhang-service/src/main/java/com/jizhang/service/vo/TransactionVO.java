package com.jizhang.service.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.jizhang.dao.entity.TransactionEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class TransactionVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long bookId;

    /** 1=支出 2=收入 3=转账 */
    private Integer type;

    /** 金额（分） */
    private Long amount;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long toAccountId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    private OffsetDateTime tradeTime;

    private String remark;

    private List<String> images;

    private List<String> tags;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer source;

    /** 记账人（家庭账本里用于展示「谁记的」与按成员统计） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    private OffsetDateTime updatedAt;

    /*
     * 以下展示字段只由管理后台接口补齐（见 AdminServiceImpl#fillDisplayNames）：
     * App 端只认 ID，不需要额外的联表查询，保持为空即可。
     */

    /** 所属账本名称 */
    private String bookName;

    /** 转出账户名称 */
    private String accountName;

    /** 转入账户名称（仅转账有值） */
    private String toAccountName;

    /** 分类名称 */
    private String categoryName;

    public static TransactionVO from(TransactionEntity entity) {
        if (entity == null) {
            return null;
        }
        TransactionVO vo = new TransactionVO();
        vo.setId(entity.getId());
        vo.setBookId(entity.getBookId());
        vo.setType(entity.getType());
        vo.setAmount(entity.getAmount());
        vo.setAccountId(entity.getAccountId());
        vo.setToAccountId(entity.getToAccountId());
        vo.setCategoryId(entity.getCategoryId());
        vo.setTradeTime(entity.getTradeTime());
        vo.setRemark(entity.getRemark());
        vo.setImages(entity.getImages());
        vo.setTags(entity.getTags());
        vo.setLongitude(entity.getLongitude());
        vo.setLatitude(entity.getLatitude());
        vo.setSource(entity.getSource());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
