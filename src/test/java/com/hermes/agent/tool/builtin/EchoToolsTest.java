package com.hermes.agent.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EchoTools} 的测试。
 * 覆盖：回声工具的方法调用和返回值。
 */
class EchoToolsTest {

    private final EchoTools tool = new EchoTools();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void echoWithValidMessage() throws Exception {
        String result = tool.echo("hello world");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertEquals("hello world", parsed.get("echoed"));
    }

    @Test
    void echoWithEmptyMessage() throws Exception {
        String result = tool.echo("");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertEquals("", parsed.get("echoed"));
    }

    @Test
    void echoWithNullMessage() throws Exception {
        String result = tool.echo(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertEquals("", parsed.get("echoed"));
    }

    @Test
    void toolHasCorrectAnnotation() throws Exception {
        // Verify the @Tool annotation is present on the echo method
        var method = EchoTools.class.getMethod("echo", String.class);
        var annotation = method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
        assertNotNull(annotation);
        assertFalse(annotation.description().isBlank());
    }
}
