package com.hermes.agent.tool.builtin;

import com.hermes.agent.config.MemoryProperties;
import com.hermes.agent.memory.MemoryStore;
import com.hermes.agent.memory.MemoryThreatDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MemoryTools} 的测试。
 */
class MemoryToolsTest {

    @TempDir
    Path tempDir;

    private MemoryTools memoryTools;

    @BeforeEach
    void setUp() {
        MemoryProperties props = new MemoryProperties();
        props.setHomeDir(tempDir.toString());
        props.setMemoryCharLimit(500);
        props.setUserCharLimit(300);
        MemoryStore store = new MemoryStore(props, new MemoryThreatDetector());
        memoryTools = new MemoryTools(store, props);
    }

    @Test
    void addMemoryTarget() {
        String result = memoryTools.memory("add", "memory", "Project uses Spring Boot 3.4", null);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("Entry added"));
    }

    @Test
    void addUserTarget() {
        String result = memoryTools.memory("add", "user", "User prefers Chinese", null);
        assertTrue(result.contains("\"success\": true"));
    }

    @Test
    void rejectSoulTarget() {
        String result = memoryTools.memory("add", "soul", "You are a funny assistant", null);
        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("Cannot write to 'soul'"));
    }

    @Test
    void rejectSoulTargetReplace() {
        String result = memoryTools.memory("replace", "soul", "new personality", "old personality");
        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("Cannot write to 'soul'"));
    }

    @Test
    void rejectSoulTargetRemove() {
        String result = memoryTools.memory("remove", "soul", "old trait", null);
        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("Cannot write to 'soul'"));
    }

    @Test
    void rejectInvalidTarget() {
        String result = memoryTools.memory("add", "invalid", "test", null);
        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("Invalid target"));
    }

    @Test
    void unknownAction() {
        String result = memoryTools.memory("delete", "memory", "test", null);
        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("Unknown action"));
    }

    @Test
    void replaceEntry() {
        memoryTools.memory("add", "memory", "Old fact", null);
        String result = memoryTools.memory("replace", "memory", "New fact", "Old fact");
        assertTrue(result.contains("\"success\": true"));
    }

    @Test
    void removeEntry() {
        memoryTools.memory("add", "memory", "Temporary fact", null);
        String result = memoryTools.memory("remove", "memory", "", "Temporary fact");
        assertTrue(result.contains("\"success\": true"));
    }

    @Test
    void memoryDisabled() {
        MemoryProperties props = new MemoryProperties();
        props.setEnabled(false);
        MemoryTools disabledTools = new MemoryTools(new MemoryStore(props, new MemoryThreatDetector()), props);
        String result = disabledTools.memory("add", "memory", "test", null);
        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("Memory is disabled"));
    }
}
