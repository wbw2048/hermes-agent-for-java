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
 * MemoryStore 单元测试。
 */
class MemoryStoreTest {

    @TempDir
    Path tempDir;

    private MemoryStore store;

    @BeforeEach
    void setUp() {
        MemoryProperties props = new MemoryProperties();
        props.setHomeDir(tempDir.toString());
        props.setMemoryCharLimit(500);
        props.setUserCharLimit(300);
        MemoryThreatDetector detector = new MemoryThreatDetector();
        store = new MemoryStore(props, detector);
    }

    @Test
    void addAndRead() {
        Map<String, Object> result = store.add("memory", "User prefers Java over Python");
        assertTrue((Boolean) result.get("success"));

        Map<String, List<String>> entries = store.getAllEntries();
        assertEquals(1, entries.get("memory").size());
        assertEquals("User prefers Java over Python", entries.get("memory").get(0));
    }

    @Test
    void addEmptyContent() {
        Map<String, Object> result = store.add("memory", "   ");
        assertFalse((Boolean) result.get("success"));
        assertEquals("Content cannot be empty.", result.get("error"));
    }

    @Test
    void addDuplicate() {
        store.add("memory", "Fact A");
        Map<String, Object> result = store.add("memory", "Fact A");
        assertTrue((Boolean) result.get("success"));
        assertEquals("Entry already exists (no duplicate added).", result.get("message"));
    }

    @Test
    void addExceedsLimit() {
        // Fill up most of the limit
        store.add("memory", "A".repeat(400));
        Map<String, Object> result = store.add("memory", "B".repeat(200));
        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("error")).contains("exceed the limit"));
    }

    @Test
    void replace() {
        store.add("memory", "User prefers Java");
        Map<String, Object> result = store.replace("memory", "Java", "User prefers Kotlin");
        assertTrue((Boolean) result.get("success"));

        Map<String, List<String>> entries = store.getAllEntries();
        assertEquals("User prefers Kotlin", entries.get("memory").get(0));
    }

    @Test
    void replaceNoMatch() {
        store.add("memory", "User prefers Java");
        Map<String, Object> result = store.replace("memory", "Python", "User prefers Kotlin");
        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("error")).contains("No entry matched"));
    }

    @Test
    void remove() {
        store.add("memory", "User prefers Java");
        Map<String, Object> result = store.remove("memory", "Java");
        assertTrue((Boolean) result.get("success"));

        Map<String, List<String>> entries = store.getAllEntries();
        assertTrue(entries.get("memory").isEmpty());
    }

    @Test
    void removeNoMatch() {
        store.add("memory", "User prefers Java");
        Map<String, Object> result = store.remove("memory", "Python");
        assertFalse((Boolean) result.get("success"));
    }

    @Test
    void addTwoTargets() {
        store.add("memory", "Project uses Spring Boot");
        store.add("user", "User is a Java developer");

        Map<String, List<String>> entries = store.getAllEntries();
        assertEquals(1, entries.get("memory").size());
        assertEquals(1, entries.get("user").size());
    }

    @Test
    void invalidTarget() {
        Map<String, Object> result = store.add("invalid", "test");
        assertFalse((Boolean) result.get("success"));
    }

    @Test
    void clear() {
        store.add("memory", "Fact A");
        store.add("memory", "Fact B");
        store.clear("memory");
        assertTrue(store.getAllEntries().get("memory").isEmpty());
    }

    @Test
    void filePersistence() {
        store.add("memory", "Persistent fact");

        // Create new store instance pointing to same directory
        MemoryProperties props = new MemoryProperties();
        props.setHomeDir(tempDir.toString());
        MemoryStore fresh = new MemoryStore(props, new MemoryThreatDetector());
        fresh.loadFromDisk();

        Map<String, List<String>> entries = fresh.getAllEntries();
        assertEquals(1, entries.get("memory").size());
        assertEquals("Persistent fact", entries.get("memory").get(0));
    }

    @Test
    void formatForSystemPromptNotEmpty() {
        store.add("memory", "Fact A");
        store.loadFromDisk(); // capture snapshot

        String block = store.formatForSystemPrompt("memory");
        assertNotNull(block);
        assertTrue(block.contains("Fact A"));
    }

    @Test
    void formatForSystemPromptEmpty() {
        store.loadFromDisk();
        assertNull(store.formatForSystemPrompt("memory"));
    }
}
