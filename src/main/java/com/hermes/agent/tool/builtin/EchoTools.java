package com.hermes.agent.tool.builtin;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * 回声工具，原样返回输入的消息。用于测试工具调用功能。
 */
@Service
public class EchoTools {

    @Tool(description = "Echo back the provided message. A simple tool for testing and debugging tool calls.")
    public String echo(@ToolParam(description = "The message to echo back") String message) {
        return "{\"echoed\": \"" + (message != null ? message : "") + "\"}";
    }
}
