package com.hermes.agent.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DateTimeTools} 的测试。
 * 覆盖：时间获取、时区处理、格式化和异常处理。
 */
class DateTimeToolsTest {

    private final DateTimeTools tool = new DateTimeTools();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void getCurrentTimeWithDefaultArgs() throws Exception {
        String result = tool.getCurrentTime(null, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue(parsed.containsKey("timezone"));
        assertTrue(parsed.containsKey("current_time"));
        assertFalse(((String) parsed.get("current_time")).isBlank());
    }

    @Test
    void getCurrentTimeWithSpecificTimezone() throws Exception {
        String result = tool.getCurrentTime("Asia/Shanghai", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertEquals("Asia/Shanghai", parsed.get("timezone"));
        assertTrue(((String) parsed.get("current_time")).contains("CST")
                || ((String) parsed.get("current_time")).matches("\\d{4}-\\d{2}-\\d{2}.*"));
    }

    @Test
    void getCurrentTimeWithCustomFormat() throws Exception {
        String result = tool.getCurrentTime("Asia/Shanghai", "yyyy-MM-dd");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        String time = (String) parsed.get("current_time");
        assertTrue(time.matches("\\d{4}-\\d{2}-\\d{2}"), "Expected date format yyyy-MM-dd but got: " + time);
    }

    @Test
    void getCurrentTimeWithInvalidTimezone() throws Exception {
        String result = tool.getCurrentTime("Invalid/Timezone", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue(parsed.containsKey("error"));
        assertTrue(((String) parsed.get("error")).contains("Invalid timezone"));
    }

    @Test
    void getCurrentTimeWithUsTimezone() throws Exception {
        String result = tool.getCurrentTime("America/New_York", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertEquals("America/New_York", parsed.get("timezone"));
    }

    @Test
    void toolHasCorrectAnnotation() throws Exception {
        var method = DateTimeTools.class.getMethod("getCurrentTime", String.class, String.class);
        var annotation = method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
        assertNotNull(annotation);
        assertFalse(annotation.description().isBlank());
    }
}
