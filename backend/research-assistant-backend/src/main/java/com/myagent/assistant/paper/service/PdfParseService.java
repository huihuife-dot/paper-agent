package com.myagent.assistant.paper.service;

/**
 * PDF 解析服务。
 * 负责从 PDF 文件中提取文本。
 */
public interface PdfParseService {

    /**
     * 从 PDF 文件中提取纯文本。
     *
     * @param filePath PDF 文件路径
     * @return PDF 中提取出的文本内容
     */
    String parseText(String filePath);
}