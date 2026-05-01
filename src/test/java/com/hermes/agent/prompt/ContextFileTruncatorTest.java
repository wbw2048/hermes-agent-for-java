package com.hermes.agent.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ContextFileTruncator} 的测试。
 */
class ContextFileTruncatorTest {

    private final ContextFileTruncator truncator = new ContextFileTruncator();

    @Test
    void shortContentUnchanged() {
        String content = "Short content.";
        assertEquals(content, truncator.truncate(content, "test.md", 100));
    }

    @Test
    void longContentTruncated() {
        String content = "A".repeat(30_000);
        String result = truncator.truncate(content, "test.md", 20_000);
        assertTrue(result.length() < content.length());
        assertTrue(result.startsWith("A".repeat(14_000)));
    }

    @Test
    void truncationMarkerPresent() {
        String content = "A".repeat(30_000);
        String result = truncator.truncate(content, "test.md", 20_000);
        assertTrue(result.contains("[...truncated test.md:"));
        assertTrue(result.contains("Use file tools to read the full file."));
    }

    @Test
    void tailPreserved() {
        // content = 20008 chars, head = 14000, tail = 4000
        // END_MARKER is at position 20000, within the last 4000 chars
        String content = "A".repeat(20_000) + "END_MARKER" + "B".repeat(8);
        String result = truncator.truncate(content, "test.md", 20_000);
        assertTrue(result.contains("END_MARKER"));
        assertTrue(result.endsWith("END_MARKER" + "B".repeat(8)));
    }

    @Test
    void usesDefaultMaxChars() {
        String content = "X".repeat(20_001);
        String result = truncator.truncate(content, "test.md");
        assertTrue(result.length() < 20_001);
        assertTrue(result.contains("[...truncated"));
    }

    @Test
    void exactBoundaryNotTruncated() {
        String content = "X".repeat(20_000);
        assertEquals(content, truncator.truncate(content, "test.md", 20_000));
    }
}
