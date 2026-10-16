package com.jizhang.service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 固定支出保存请求。
 * <p>
 * {@code id} 由客户端生成（毫秒时间戳左移 20 位 + 20 位随机数）：同一个 id 重复提交视为修改，
 * 不存在则新建。没有业务键可用 —— 同名固定支出是允许的（例如两笔不同额度的房租），
 * 所以幂等只能落在客户端 id 上。
 */
@Data
public class RecurringExpenseSaveRequest {

    @NotNull(message = "固定支出 ID 不能为空")
    private Long id;

    @NotNull(message = "账本不能为空")
    private Long bookId;

    @NotBlank(message = "名称不能为空")
    @Size(max = 64, message = "名称最长 64 个字符")
    private String name;

    /** 0 或 null 表示未指定分类 */
    private Long categoryId;

    /** 频率：1=每周 2=每月 3=每年；缺省按每月处理 */
    private Integer freq;

    /** 每周用：1=周一 … 7=周日；缺省按周一 */
    private Integer dayOfWeek;

    /** 每月 / 每年用：1-31，超出当月天数时按当月最后一天；缺省按 1 号 */
    private Integer dayOfMonth;

    /** 每年用：1-12；缺省按 1 月 */
    private Integer monthOfYear;

    @NotNull(message = "金额不能为空")
    @Min(value = 1, message = "金额必须大于 0")
    private Long amount;

    /** 生效首日，格式 yyyy-MM-dd；不传按今天 */
    private String startDate;

    /** 失效日，格式 yyyy-MM-dd；可空，表示长期有效 */
    private String endDate;

    /** 是否启用；缺省视为启用 */
    private Boolean enabled;

    @Size(max = 255, message = "备注最长 255 个字符")
    private String remark;
}
