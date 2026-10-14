package com.jizhang.service.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 资产分布报表。
 */
@Data
public class AssetReportVO {

    /** 总资产（分），仅统计 includeInTotal = 1 的账户 */
    private long totalAssets;

    private List<AccountAssetVO> accounts = new ArrayList<>();
}
