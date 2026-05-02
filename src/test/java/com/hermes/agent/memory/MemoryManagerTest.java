package com.hermes.agent.memory;

import com.hermes.agent.config.MemoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryManager 单元测试。
 */
class MemoryManagerTest {

    @TempDir
    Path tempDir;

    private MemoryManager manager;
    private BuiltinMemoryProvider builtinProvider;
    private MemoryStore store;

    @BeforeEach
    void setUp() {
        MemoryProperties props = new MemoryProperties();
        props.setHomeDir(tempDir.toString());
        props.setEnabled(true);

        MemoryProperties storeProps = new MemoryProperties();
        storeProps.setHomeDir(tempDir.toString());
        MemoryThreatDetector detector = new MemoryThreatDetector();
        this.store = new MemoryStore(storeProps, detector);

        builtinProvider = new BuiltinMemoryProvider(store);
        manager = new MemoryManager(props);
        manager.addProvider(builtinProvider);
    }

    @Test
    void addBuiltinProvider() {
        assertEquals(1, manager.getProviders().size());
        assertEquals("builtin", manager.getProviders().get(0).name());
    }

    @Test
    void rejectSecondExternalProvider() {
        // Create a second fake external provider
        MemoryProvider external2 = new FakeProvider("external2");
        manager.addProvider(external2);

        // Create another external provider — should be rejected
        MemoryProvider external3 = new FakeProvider("external3");
        manager.addProvider(external3);

        // Only builtin + external2 should be registered
        assertEquals(2, manager.getProviders().size());
    }

    @Test
    void buildSystemPromptFromProvider() {
        manager.initializeAll("test-session");
        // The builtinProvider wraps the same store we created
        builtinProvider.getMemoryStore().add("memory", "Fact A");
        // Reload to capture snapshot
        builtinProvider.getMemoryStore().loadFromDisk();

        String block = manager.buildSystemPrompt();
        assertTrue(block.contains("Fact A"));
    }

    @Test
    void prefetchAll() {
        String result = manager.prefetchAll("hello");
        // Builtin provider returns empty by default for prefetch
        assertEquals("", result);
    }

    @Test
    void syncAll() {
        // Should not throw
        manager.syncAll("user says hi", "assistant says hello");
    }

    @Test
    void getAllToolSchemas() {
        List<Map<String, Object>> schemas = manager.getAllToolSchemas();
        // Builtin provider returns empty schemas (tools via @Tool annotation)
        assertTrue(schemas.isEmpty());
    }

    @Test
    void disabledMemory() {
        MemoryProperties props = new MemoryProperties();
        props.setEnabled(false);
        MemoryManager disabledManager = new MemoryManager(props);
        disabledManager.addProvider(new FakeProvider("fake"));

        assertEquals("", disabledManager.buildSystemPrompt());
        assertEquals("", disabledManager.prefetchAll("test"));
        disabledManager.syncAll("a", "b");
        disabledManager.queuePrefetchAll("test");
    }

    // Simple fake external provider for testing
    private static class FakeProvider implements MemoryProvider {
        private final String name;

        FakeProvider(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void initialize(String sessionId, Map<String, Object> kwargs) {
        }

        @Override
        public List<Map<String, Object>> getToolSchemas() {
            return List.of();
        }

        @Override
        public String handleToolCall(String toolName, Map<String, Object> args) {
            return null;
        }
    }
}
