package com.hermes.agent.controller;

import com.hermes.agent.config.ErrorHandlingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallTrackerIsolationTest {

    @Test
    void toolExceptionIsolatedWhenEnabled() throws Exception {
        ErrorHandlingProperties props = new ErrorHandlingProperties();
        props.setToolErrorIsolationEnabled(true);
        ToolCallTracker tracker = new ToolCallTracker(props);

        FaultyTool faultyTool = new FaultyTool();
        Object proxy = tracker.wrap(faultyTool);

        tracker.startTracking();
        Object result = proxy.getClass().getMethod("broken").invoke(proxy);
        tracker.stopTracking();

        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("工具执行失败"));
    }

    @Test
    void toolExceptionRethrownWhenDisabled() {
        ErrorHandlingProperties props = new ErrorHandlingProperties();
        props.setToolErrorIsolationEnabled(false);
        ToolCallTracker tracker = new ToolCallTracker(props);

        FaultyTool faultyTool = new FaultyTool();
        Object proxy = tracker.wrap(faultyTool);

        tracker.startTracking();
        assertThrows(Exception.class, () -> {
            proxy.getClass().getMethod("broken").invoke(proxy);
        });
        tracker.stopTracking();
    }

    @Test
    void toolErrorRecordedInTracker() throws Exception {
        ErrorHandlingProperties props = new ErrorHandlingProperties();
        props.setToolErrorIsolationEnabled(true);
        ToolCallTracker tracker = new ToolCallTracker(props);

        FaultyTool faultyTool = new FaultyTool();
        Object proxy = tracker.wrap(faultyTool);

        tracker.startTracking();
        proxy.getClass().getMethod("broken").invoke(proxy);
        var calls = tracker.stopTracking();

        assertEquals(1, calls.size());
        assertNotNull(calls.get(0).error());
        assertEquals("broken", calls.get(0).toolName());
    }

    @Test
    void successfulToolCallRecordedCorrectly() throws Exception {
        ErrorHandlingProperties props = new ErrorHandlingProperties();
        ToolCallTracker tracker = new ToolCallTracker(props);

        WorkingTool workingTool = new WorkingTool();
        Object proxy = tracker.wrap(workingTool);

        tracker.startTracking();
        Object result = proxy.getClass().getMethod("greet", String.class).invoke(proxy, "World");
        var calls = tracker.stopTracking();

        assertEquals(1, calls.size());
        assertEquals("greet", calls.get(0).toolName());
        assertEquals("Hello World", result);
        assertNull(calls.get(0).error());
    }

    /**
     * 工具类：总是抛出异常。
     */
    static class FaultyTool {
        @Tool(name = "broken", description = "A method that always fails")
        public String broken() {
            throw new RuntimeException("simulated tool failure");
        }
    }

    /**
     * 工具类：正常工作。
     */
    static class WorkingTool {
        @Tool(name = "greet", description = "A working tool")
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
