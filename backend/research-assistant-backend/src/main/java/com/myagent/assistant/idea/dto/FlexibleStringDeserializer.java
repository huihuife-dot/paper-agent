package com.myagent.assistant.idea.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 灵活的 String 反序列化器。
 *
 * 用于处理大模型返回 JSON 中字段既可能是 String 也可能是 Array 的情况。
 * 当大模型返回 ["a", "b"] 时自动转换为 "a；b"（中文分号分隔），
 * 当大模型返回普通字符串时原样返回。
 */
public class FlexibleStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        // null → null
        if (p.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }

        // JSON 数组 → 用中文分号拼接
        if (p.currentToken() == JsonToken.START_ARRAY) {
            List<String> items = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                if (p.currentToken() != JsonToken.VALUE_NULL) {
                    items.add(p.getText());
                }
            }
            return items.isEmpty() ? null : String.join("；", items);
        }

        // 普通字符串 / 数字 → 原样返回
        return p.getValueAsString();
    }
}
