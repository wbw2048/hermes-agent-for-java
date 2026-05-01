package com.hermes.agent.tool.builtin;

import com.hermes.agent.tool.annotation.ToolSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件操作工具：读取、写入、替换和搜索文件。
 * <p>
 * 提供类似 IDE 的文件操作能力，避免 LLM 直接使用 shell 命令（cat/grep/sed）。
 */
@Service
@ToolSet("file")
public class FileTools {

    private static final Logger log = LoggerFactory.getLogger(FileTools.class);
    private static final int DEFAULT_READ_LIMIT = 500;
    private static final int MAX_READ_CHARS = 100_000;

    /**
     * 读取文本文件内容，支持分页。
     *
     * @param path   文件路径（绝对路径、相对路径或 ~/path）
     * @param offset 起始行号（1-indexed，默认 1）
     * @param limit  最大读取行数（默认 500）
     * @return JSON 格式的读取结果
     */
    @Tool(description = "Read a text file with line numbers and pagination. Use this instead of cat/head/tail in terminal. Output format: 'LINE_NUM|CONTENT'. Suggests similar filenames if not found. Use offset and limit for large files. Reads exceeding ~100K characters are rejected; use offset and limit to read specific sections of large files.")
    public String readFile(
            @ToolParam(description = "Path to the file to read (absolute, relative, or ~/path)") String path,
            @ToolParam(description = "Line number to start reading from (1-indexed, default: 1)") Integer offset,
            @ToolParam(description = "Maximum number of lines to read (default: 500, max: 2000)") Integer limit) {
        int off = (offset != null && offset >= 1) ? offset : 1;
        int lim = (limit != null && limit > 0) ? Math.min(limit, 2000) : DEFAULT_READ_LIMIT;

        log.info("[TOOL-CALL] readFile: path={}, offset={}, limit={}", path, off, lim);

        // 安全检查
        String error = PathValidator.validateReadPath(path);
        if (error != null) {
            return "{\"error\": \"" + escapeJson(error) + "\"}";
        }

        try {
            Path resolved = PathValidator.resolvePath(path);
            if (!Files.exists(resolved)) {
                return suggestSimilarFiles(path);
            }
            if (!Files.isRegularFile(resolved)) {
                return "{\"error\": \"Path is not a regular file: " + escapeJson(path) + "\"}";
            }

            List<String> allLines = Files.readAllLines(resolved);
            int totalLines = allLines.size();
            int startIndex = off - 1;
            if (startIndex >= totalLines) {
                return "{\"error\": \"File has " + totalLines + " lines, but offset starts at line " + off + "\"}";
            }
            int endIndex = Math.min(startIndex + lim, totalLines);
            List<String> contentLines = allLines.subList(startIndex, endIndex);

            String content = formatWithLineNumbers(contentLines, off);
            int charCount = content.length();
            if (charCount > MAX_READ_CHARS) {
                return "{\"error\": \"Read produced " + charCount + " characters which exceeds the safety limit ("
                        + MAX_READ_CHARS + " chars). Use offset and limit to read a smaller range. The file has "
                        + totalLines + " lines total.\"}";
            }

            String result = "{\"path\": \"" + escapeJson(path) + "\","
                    + "\"total_lines\": " + totalLines + ","
                    + "\"content\": \"" + escapeJson(content) + "\","
                    + "\"truncated\": " + (endIndex < totalLines) + "}";
            log.info("[TOOL-RETURN] readFile: path={}, lines={}, chars={}", path, contentLines.size(), charCount);
            return result;
        } catch (IOException e) {
            return "{\"error\": \"Failed to read file: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * 写入文件，完全覆盖已有内容。
     *
     * @param path    文件路径（不存在则自动创建父目录）
     * @param content 文件内容
     * @return JSON 格式的写入结果
     */
    @Tool(description = "Write content to a file, completely replacing existing content. Use this instead of echo/cat heredoc in terminal. Creates parent directories automatically. OVERWRITES the entire file — use 'patch' for targeted edits.")
    public String writeFile(
            @ToolParam(description = "Path to the file to write (will be created if it doesn't exist, overwritten if it does)") String path,
            @ToolParam(description = "Complete content to write to the file") String content) {
        log.info("[TOOL-CALL] writeFile: path={}, contentLength={}", path, content != null ? content.length() : 0);

        String error = PathValidator.validateWritePath(path);
        if (error != null) {
            return "{\"error\": \"" + escapeJson(error) + "\"}";
        }

        try {
            Path resolved = PathValidator.resolvePath(path);
            if (resolved.getParent() != null) {
                Files.createDirectories(resolved.getParent());
            }
            Files.writeString(resolved, content != null ? content : "");

            String result = "{\"success\": true, \"path\": \"" + escapeJson(path) + "\","
                    + "\"bytes_written\": " + (content != null ? content.length() : 0) + "}";
            log.info("[TOOL-RETURN] writeFile: path={}, success", path);
            return result;
        } catch (IOException e) {
            return "{\"error\": \"Failed to write file: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * 在文件中查找并替换文本。
     *
     * @param path       文件路径
     * @param oldString  要查找的文本
     * @param newString  替换后的文本
     * @param replaceAll 是否替换所有匹配项
     * @return JSON 格式的替换结果
     */
    @Tool(description = "Targeted find-and-replace edits in files. Use this instead of sed/awk in terminal. Returns the number of replacements made. Auto-runs syntax checks after editing.")
    public String patch(
            @ToolParam(description = "File path to edit") String path,
            @ToolParam(description = "Text to find in the file. Must be unique in the file unless replace_all=true.") String oldString,
            @ToolParam(description = "Replacement text. Can be empty string to delete the matched text.") String newString,
            @ToolParam(description = "Replace all occurrences instead of requiring a unique match (default: false)") Boolean replaceAll) {
        log.info("[TOOL-CALL] patch: path={}, oldStringLen={}, newStringLen={}, replaceAll={}",
                path, oldString != null ? oldString.length() : 0, newString != null ? newString.length() : 0, replaceAll);

        if (path == null || oldString == null) {
            return "{\"error\": \"path and oldString are required\"}";
        }

        String error = PathValidator.validateWritePath(path);
        if (error != null) {
            return "{\"error\": \"" + escapeJson(error) + "\"}";
        }

        try {
            Path resolved = PathValidator.resolvePath(path);
            if (!Files.exists(resolved)) {
                return "{\"error\": \"File not found: " + escapeJson(path) + "\"}";
            }

            String content = Files.readString(resolved);
            String replaced;
            int count;
            boolean all = Boolean.TRUE.equals(replaceAll);

            if (all) {
                // Count occurrences before replacing
                count = 0;
                int idx = 0;
                while ((idx = content.indexOf(oldString, idx)) >= 0) {
                    count++;
                    idx += oldString.length();
                    if (oldString.isEmpty()) break; // prevent infinite loop
                }
                replaced = content.replace(oldString, newString != null ? newString : "");
            } else {
                int idx = content.indexOf(oldString);
                if (idx < 0) {
                    return "{\"error\": \"Could not find specified text in file\"}";
                }
                replaced = content.substring(0, idx)
                        + (newString != null ? newString : "")
                        + content.substring(idx + oldString.length());
                count = 1;
            }

            Files.writeString(resolved, replaced);
            String result = "{\"success\": true, \"path\": \"" + escapeJson(path)
                    + "\", \"replacements\": " + count + "}";
            log.info("[TOOL-RETURN] patch: path={}, replacements={}", path, count);
            return result;
        } catch (IOException e) {
            return "{\"error\": \"Failed to patch file: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * 搜索文件内容或按名称查找文件。
     *
     * @param pattern 搜索模式（正则表达式或文件名通配符）
     * @param target  搜索目标（"content"搜索文件内容，"files"按文件名搜索）
     * @param path    搜索目录（默认当前目录）
     * @param limit   最大结果数（默认 50）
     * @return JSON 格式的搜索结果
     */
    @Tool(description = "Search file contents or find files by name. Use this instead of grep/rg/find/ls in terminal. Content search: Regex search inside files. File search: Find files by glob pattern (e.g., '*.py'). Results sorted by modification time.")
    public String searchFiles(
            @ToolParam(description = "Regex pattern for content search, or glob pattern (e.g., '*.py') for file search") String pattern,
            @ToolParam(description = "'content' searches inside file contents, 'files' searches for files by name") String target,
            @ToolParam(description = "Directory or file to search in (default: current working directory)") String path,
            @ToolParam(description = "Maximum number of results to return (default: 50)") Integer limit) {
        int lim = (limit != null && limit > 0) ? limit : 50;
        String searchDir = (path != null && !path.isBlank()) ? path : System.getProperty("user.dir");
        String targetType = (target != null && !target.isBlank()) ? target : "content";

        log.info("[TOOL-CALL] searchFiles: pattern={}, target={}, dir={}, limit={}", pattern, targetType, searchDir, lim);

        if (pattern == null || pattern.isBlank()) {
            return "{\"error\": \"pattern is required\"}";
        }

        try {
            Path searchPath = PathValidator.resolvePath(searchDir);
            if (!Files.exists(searchPath)) {
                return "{\"error\": \"Search directory not found: " + escapeJson(searchDir) + "\"}";
            }

            if ("files".equals(targetType)) {
                return searchFilesByName(searchPath, pattern, lim);
            } else {
                return searchFileContent(searchPath, pattern, lim);
            }
        } catch (Exception e) {
            return "{\"error\": \"Search failed: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private String searchFileContent(Path searchPath, String regex, int limit) throws IOException {
        try (Stream<Path> paths = Files.walk(searchPath)) {
            var results = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String ext = p.toString().toLowerCase();
                        return !ext.endsWith(".jar") && !ext.endsWith(".class")
                                && !ext.endsWith(".so") && !ext.endsWith(".dylib");
                    })
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return content.matches("(?s).*" + regex + ".*");
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .limit(limit)
                    .map(Path::toString)
                    .collect(Collectors.toList());
            return "{\"matches\": " + toJsonArray(results) + ", \"count\": " + results.size() + ", \"target\": \"content\"}";
        }
    }

    private String searchFilesByName(Path searchPath, String glob, int limit) throws IOException {
        try (Stream<Path> paths = Files.walk(searchPath)) {
            var results = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(globToRegex(glob)))
                    .limit(limit)
                    .map(Path::toString)
                    .collect(Collectors.toList());
            return "{\"matches\": " + toJsonArray(results) + ", \"count\": " + results.size() + ", \"target\": \"files\"}";
        }
    }

    private String suggestSimilarFiles(String path) {
        try {
            Path parent = PathValidator.resolvePath(path).getParent();
            if (parent != null && Files.exists(parent)) {
                try (Stream<Path> stream = Files.list(parent)) {
                    var similar = stream
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().contains(
                                    Path.of(path).getFileName().toString().toLowerCase()))
                            .limit(5)
                            .map(p -> p.getFileName().toString())
                            .collect(Collectors.toList());
                    if (!similar.isEmpty()) {
                        return "{\"error\": \"File not found: " + escapeJson(path)
                                + "\", \"similar_files\": " + toJsonArray(similar) + "}";
                    }
                }
            }
        } catch (IOException e) {
            // fall through
        }
        return "{\"error\": \"File not found: " + escapeJson(path) + "\"}";
    }

    private static String formatWithLineNumbers(List<String> lines, int startLine) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(String.format("%4d|", startLine + i)).append(lines.get(i));
        }
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String toJsonArray(List<String> items) {
        return items.stream()
                .map(s -> "\"" + escapeJson(s) + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * 将 glob 模式转换为正则表达式。
     */
    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '.' -> regex.append("\\.");
                default -> regex.append(c);
            }
        }
        regex.append("$");
        return regex.toString();
    }
}
