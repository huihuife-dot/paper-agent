package com.myagent.assistant.llm;

import java.util.function.Consumer;
import java.util.List;

/**
 * 大模型调用服务。
 *
 * 项目主流程只依赖该接口，
 * 不直接依赖 DeepSeek、智谱、Qwen 等具体供应商。
 */
public interface LlmService {

    /**
     * 根据 RAG prompt 生成回答。
     *
     * @param prompt 已构造好的 RAG prompt
     * @return 大模型生成的回答
     */
    String generateAnswer(String prompt);

    /** 新聊天入口；旧字符串接口继续服务画像、路由等单次任务。 */
    default String generateMessages(List<LlmMessage> messages) {
        throw new UnsupportedOperationException("当前模型服务尚未实现多消息对话，请关闭 chat.context.enabled 或更换已支持的 provider");
    }

    default String generateMessagesStream(List<LlmMessage> messages, Consumer<String> onDelta) {
        String answer = generateMessages(messages);
        if (onDelta != null && answer != null && !answer.isEmpty()) onDelta.accept(answer);
        return answer;
    }

    /**
     * 流式生成回答。默认实现保持第三方实现兼容，但只会回调一次完整答案；
     * OpenAI-compatible 实现会覆盖为真正的增量输出。
     */
    default String generateAnswerStream(String prompt, Consumer<String> onDelta) {
        String answer = generateAnswer(prompt);
        if (onDelta != null && answer != null && !answer.isEmpty()) {
            onDelta.accept(answer);
        }
        return answer;
    }

    /**
     * 当前模型供应商名称。
     *
     * 例如：fake、deepseek、zhipu、qwen。
     */
    String provider();

    /**
     * 当前模型名称。
     *
     * 例如：deepseek-chat、glm-4-flash、qwen-plus。
     */
    String modelName();
}
