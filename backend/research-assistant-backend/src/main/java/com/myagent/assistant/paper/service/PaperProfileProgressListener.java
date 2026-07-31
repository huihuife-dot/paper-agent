package com.myagent.assistant.paper.service;

/**
 * 文献画像生成进度监听器。
 *
 * 画像生成服务只报告进度事件，不直接依赖任务表，避免画像生成逻辑和任务持久化耦合。
 */
public interface PaperProfileProgressListener {

    PaperProfileProgressListener NOOP = new PaperProfileProgressListener() {
    };

    default void onPreparing() {
    }

    default void onSectionProgress(int processedSections, int totalSections) {
    }

    default void onGeneratingProfile() {
    }

    default void onSavingResult() {
    }
}
