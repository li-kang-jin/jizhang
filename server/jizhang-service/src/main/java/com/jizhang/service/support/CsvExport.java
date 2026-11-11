package com.jizhang.service.support;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 管理后台 CSV 导出工具。
 * <p>
 * 不引第三方 Excel 依赖（easyexcel / poi）：后台导出的诉求是「拉下来自己筛一筛」，
 * CSV 足够，而且服务端零依赖、浏览器 / Excel / WPS 都能直接打开。
 * <p>
 * 三个容易踩的坑在这里统一处理：
 * <ol>
 *   <li>编码：UTF-8 必须带 BOM，否则 Excel 按本地编码解析，中文全是乱码；</li>
 *   <li>转义：备注里一个逗号或换行就能把整张表撑散列，含特殊字符的单元格要包引号并把内部引号翻倍；</li>
 *   <li>金额：库里存的是「分」，不能用浮点除法转元，否则会出现 0.30000000000000004。</li>
 * </ol>
 */
public final class CsvExport {

    /** Excel 靠 BOM 判断 UTF-8，缺了它中文就是乱码 */
    private static final String BOM = "\uFEFF";

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private CsvExport() {
    }

    /**
     * 生成 CSV 内容。
     *
     * @param headers   表头
     * @param rows      数据行，每行的列数需与表头一致
     * @param truncated 是否因上限被截断
     */
    public static CsvResult build(List<String> headers, List<List<String>> rows, boolean truncated) {
        StringBuilder sb = new StringBuilder(BOM.length() + rows.size() * 128);
        sb.append(BOM);
        appendLine(sb, headers);
        for (List<String> row : rows) {
            appendLine(sb, row);
        }
        return new CsvResult(sb.toString().getBytes(StandardCharsets.UTF_8), rows.size(), truncated);
    }

    private static void appendLine(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        // 用 \r\n：Excel 与 Windows 记事本都认，纯 \n 在记事本里会连成一行
        sb.append("\r\n");
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        return needQuote ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    /** 金额（分）→ 元。用 BigDecimal 移位，避免浮点误差 */
    public static String yuan(Long fen) {
        return fen == null ? "" : BigDecimal.valueOf(fen).movePointLeft(2).toPlainString();
    }

    /** 时间统一按服务器本地时区展示，与后台页面看到的时刻一致 */
    public static String time(OffsetDateTime value) {
        return value == null ? "" : TIME_FORMATTER.format(value);
    }

    /** 空值统一成空字符串：CSV 里 null 与 "" 没有区别，别让 "null" 出现在表格里 */
    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
