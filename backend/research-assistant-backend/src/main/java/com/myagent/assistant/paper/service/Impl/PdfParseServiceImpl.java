package com.myagent.assistant.paper.service.impl;

import com.myagent.assistant.paper.service.PdfParseService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * PDF 解析服务实现。
 *
 * 当前第一版只处理可复制文本的 PDF。
 * 如果是扫描版 PDF，后期需要接入 OCR。
 */
@Service
public class PdfParseServiceImpl implements PdfParseService {

    @Override
    public String parseText(String filePath) {
        File file = new File(filePath);

        if (!file.exists()) {
            throw new RuntimeException("PDF 文件不存在：" + filePath);
        }

        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();

            // 提取 PDF 中的纯文本
            return stripper.getText(document);
        } catch (IOException e) {
            throw new RuntimeException("PDF 文本解析失败", e);
        }
    }
}
