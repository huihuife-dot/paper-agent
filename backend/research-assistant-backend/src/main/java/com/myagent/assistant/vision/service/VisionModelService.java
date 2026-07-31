package com.myagent.assistant.vision.service;

import java.nio.file.Path;

/** 视觉模型边界：业务层只传受控资产文件，不接触厂商请求格式或 API Key。 */
public interface VisionModelService {
    String analyze(String assetType, String caption, String nearbyText, Path image);
    String analysisVersion();
}
