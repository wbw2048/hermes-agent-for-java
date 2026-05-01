package com.hermes.agent.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.annotation.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FileTools} 的测试。
 * 覆盖：文件读写、替换、搜索、分页、错误处理。
 */
class FileToolsTest {

    private final FileTools tools = new FileTools();
    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Line 1\nLine 2\nLine 3\nLine 4\nLine 5\n");
    }

    @Test
    void readFileBasic() throws Exception {
        String result = tools.readFile(testFile.toString(), null, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertFalse(parsed.containsKey("error"));
        assertTrue(((String) parsed.get("content")).contains("Line 1"));
        assertEquals(5, parsed.get("total_lines"));
    }

    @Test
    void readFileWithPagination() throws Exception {
        String result = tools.readFile(testFile.toString(), 2, 2);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        String content = (String) parsed.get("content");
        assertTrue(content.contains("Line 2"));
        assertTrue(content.contains("Line 3"));
        assertFalse(content.contains("Line 1"));
    }

    @Test
    void readFileNotFound() throws Exception {
        String result = tools.readFile(tempDir.resolve("nonexistent.txt").toString(), null, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue(parsed.containsKey("error"));
    }

    @Test
    void writeFileBasic() throws Exception {
        Path newFile = tempDir.resolve("new.txt");
        String result = tools.writeFile(newFile.toString(), "Hello World");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue((Boolean) parsed.get("success"));

        assertEquals("Hello World", Files.readString(newFile));
    }

    @Test
    void writeFileCreatesParentDirectories() throws Exception {
        Path newFile = tempDir.resolve("a/b/c/file.txt");
        String result = tools.writeFile(newFile.toString(), "nested content");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue((Boolean) parsed.get("success"));
        assertTrue(Files.exists(newFile));
    }

    @Test
    void writeFileOverwritesExisting() throws Exception {
        String result = tools.writeFile(testFile.toString(), "Overwritten content");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue((Boolean) parsed.get("success"));
        assertEquals("Overwritten content", Files.readString(testFile));
    }

    @Test
    void patchReplaceAll() throws Exception {
        String result = tools.patch(testFile.toString(), "Line", "Row", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue((Boolean) parsed.get("success"));
        int replacements = (Integer) parsed.get("replacements");
        assertEquals(5, replacements);

        String content = Files.readString(testFile);
        assertEquals("Row 1\nRow 2\nRow 3\nRow 4\nRow 5\n", content);
    }

    @Test
    void patchSingleReplace() throws Exception {
        String result = tools.patch(testFile.toString(), "Line 2", "Line TWO", false);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue((Boolean) parsed.get("success"));
        assertEquals(1, parsed.get("replacements"));

        String content = Files.readString(testFile);
        assertTrue(content.contains("Line TWO"));
    }

    @Test
    void patchNotFound() throws Exception {
        String result = tools.patch(testFile.toString(), "nonexistent text", "replacement", false);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue(parsed.containsKey("error"));
    }

    @Test
    void searchFilesByName() throws Exception {
        Files.writeString(tempDir.resolve("hello.java"), "class Hello {}");
        Files.writeString(tempDir.resolve("world.java"), "class World {}");
        Files.writeString(tempDir.resolve("notes.txt"), "some notes");

        String result = tools.searchFiles("*.java", "files", tempDir.toString(), 50);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertEquals("files", parsed.get("target"));
        assertEquals(2, parsed.get("count"));
    }

    @Test
    void searchFileContent() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "hello world");
        Files.writeString(tempDir.resolve("b.txt"), "goodbye world");
        Files.writeString(tempDir.resolve("c.txt"), "no match here");

        String result = tools.searchFiles("hello", "content", tempDir.toString(), 50);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertEquals("content", parsed.get("target"));
        assertEquals(1, parsed.get("count"));
    }

    @Test
    void toolHasCorrectAnnotation() throws Exception {
        var method = FileTools.class.getMethod("readFile", String.class, Integer.class, Integer.class);
        var annotation = method.getAnnotation(Tool.class);
        assertNotNull(annotation);
        assertFalse(annotation.description().isBlank());
    }
}
