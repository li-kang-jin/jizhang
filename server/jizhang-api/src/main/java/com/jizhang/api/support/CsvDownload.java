package com.jizhang.api.support;

import com.jizhang.service.support.CsvResult;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 管理后台 CSV 下载响应。
 * <p>
 * 导出的响应体是文件而不是 {@code Result<T>} 包装的 JSON：前端拿到的是 Blob，
 * 浏览器直接落盘。行数与是否截断通过响应头回传，前端据此提示管理员
 * 「导了多少条 / 是不是被上限截断了」。
 */
public final class CsvDownload {

    /** 实际导出的数据行数（不含表头） */
    public static final String HEADER_ROWS = "X-Export-Rows";

    /** 是否触达导出上限被截断 */
    public static final String HEADER_TRUNCATED = "X-Export-Truncated";

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private CsvDownload() {
    }

    public static ResponseEntity<byte[]> of(CsvResult result, String baseName) {
        String filename = baseName + "-" + FILE_TIME.format(LocalDateTime.now()) + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        headers.set(HEADER_ROWS, String.valueOf(result.rows()));
        headers.set(HEADER_TRUNCATED, String.valueOf(result.truncated()));
        // 跨域部署时浏览器默认只暴露简单响应头，不显式 expose 的话前端读不到这两个值
        headers.setAccessControlExposeHeaders(List.of(HEADER_ROWS, HEADER_TRUNCATED));
        return new ResponseEntity<>(result.content(), headers, HttpStatus.OK);
    }
}
