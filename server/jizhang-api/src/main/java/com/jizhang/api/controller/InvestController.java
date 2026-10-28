package com.jizhang.api.controller;

import com.jizhang.common.api.Result;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.service.InvestService;
import com.jizhang.service.dto.InvestSyncRequest;
import com.jizhang.service.vo.InvestDataVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端投资持仓接口。
 * <p>
 * 只有两个端点：一次读全量、一次批写。投资的三张表（持仓 / 估值 / 成本流水）总是一起用，
 * 拆成按资源的增删改查会让客户端为一次渲染发三次请求，还得处理「其中一个失败」的中间态。
 * <p>
 * 买入卖出不在这里记 —— 那仍然是普通的转账账单，投资只表达「钱落在哪个品种上、现在值多少」。
 */
@Tag(name = "投资持仓", description = "基金 / 股票的持仓、市值快照与成本流水（买入仍是转账，不产生收支）")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/app/invest")
public class InvestController {

    private final InvestService investService;

    public InvestController(InvestService investService) {
        this.investService = investService;
    }

    @Operation(summary = "投资数据全量拉取", description = "返回该账本的全部持仓、估值快照与成本流水")
    @GetMapping
    public Result<InvestDataVO> load(@RequestParam Long bookId) {
        return Result.success(investService.load(bookId));
    }

    @Operation(
            summary = "投资数据批量同步",
            description = "按客户端 id 幂等写入；流水按持仓全量覆盖，删除持仓会级联清掉它的估值与流水"
    )
    @PostMapping("/sync")
    public Result<Void> sync(@Valid @RequestBody InvestSyncRequest request) {
        investService.sync(request);
        return Result.success();
    }
}
