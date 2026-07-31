package com.myagent.assistant.idea.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * FlexibleStringDeserializer 单元测试。
 *
 * 验证：
 * 1. 普通字符串原样返回
 * 2. JSON 数组自动拼接为中文分号分隔字符串
 * 3. null 返回 null
 * 4. 空数组返回 null
 */
class FlexibleStringDeserializerTest {

    @Data
    public static class TestBean {
        @JsonDeserialize(using = FlexibleStringDeserializer.class)
        private String value;
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldReturnStringAsIs() throws Exception {
        TestBean result = mapper.readValue("{\"value\":\"创新点A；创新点B\"}", TestBean.class);
        assertEquals("创新点A；创新点B", result.getValue());
    }

    @Test
    void shouldJoinArrayWithChineseSemicolon() throws Exception {
        TestBean result = mapper.readValue(
                "{\"value\":[\"创新点A\",\"创新点B\",\"创新点C\"]}", TestBean.class);
        assertEquals("创新点A；创新点B；创新点C", result.getValue());
    }

    @Test
    void shouldReturnNullForJsonNull() throws Exception {
        TestBean result = mapper.readValue("{\"value\":null}", TestBean.class);
        assertNull(result.getValue());
    }

    @Test
    void shouldReturnNullForEmptyArray() throws Exception {
        TestBean result = mapper.readValue("{\"value\":[]}", TestBean.class);
        assertNull(result.getValue());
    }

    @Test
    void shouldHandleSingleElementArray() throws Exception {
        TestBean result = mapper.readValue("{\"value\":[\"只有一个\"]}", TestBean.class);
        assertEquals("只有一个", result.getValue());
    }
}
