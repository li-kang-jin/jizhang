package com.jizhang.service.support;

/**
 * 一次导出的结果。
 * <p>
 * 不只返回字节：管理后台需要告诉管理员「实际导了多少条、有没有因为上限被截断」，
 * 否则全平台数据量一大，导出的表格悄悄少一截而界面毫无提示。
 *
 * @param content   CSV 文件内容（UTF-8，带 BOM）
 * @param rows      实际导出的数据行数（不含表头）
 * @param truncated 是否触达上限被截断
 */
public record CsvResult(byte[] content, long rows, boolean truncated) {
}
