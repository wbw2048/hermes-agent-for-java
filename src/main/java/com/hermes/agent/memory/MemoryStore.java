package com.hermes.agent.memory;

import com.hermes.agent.config.MemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 有界、文件持久化的长期记忆存储。
 * <p>
 * 维护两个并行的记忆存储：
 * <ul>
 *   <li>MEMORY.md：智能体的个人笔记（环境事实、项目约定、工具经验）</li>
 *   <li>USER.md：关于用户的信息（偏好、沟通风格、工作流习惯）</li>
 * </ul>
 * 两者在会话启动时作为冻结快照注入系统提示，运行时修改不会影响当前会话。
 */
@Component
public class MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(MemoryStore.class);
    static final String ENTRY_DELIMITER = "\n§\n";

    private final MemoryProperties properties;
    private final MemoryThreatDetector threatDetector;

    final List<String> memoryEntries = new ArrayList<>();
    final List<String> userEntries = new ArrayList<>();

    /** 冻结快照——加载时捕获，用于系统提示注入，会话期间不变 */
    private Map<String, String> systemPromptSnapshot = Map.of("memory", "", "user", "");

    public MemoryStore(MemoryProperties properties, MemoryThreatDetector threatDetector) {
        this.properties = properties;
        this.threatDetector = threatDetector;
    }

    /**
     * 从磁盘加载记忆条目到内存，并捕获系统提示快照。
     */
    public void loadFromDisk() {
        Path memDir = Path.of(properties.getHomeDir());
        ensureDir(memDir);

        memoryEntries.clear();
        memoryEntries.addAll(readFile(memDir.resolve("MEMORY.md")));
        deduplicate(memoryEntries);

        userEntries.clear();
        userEntries.addAll(readFile(memDir.resolve("USER.md")));
        deduplicate(userEntries);

        systemPromptSnapshot = Map.of(
            "memory", renderBlock("memory", memoryEntries),
            "user", renderBlock("user", userEntries)
        );

        log.info("Memory loaded: memory={} entries, user={} entries", memoryEntries.size(), userEntries.size());
    }

    /**
     * 添加新条目。
     *
     * @param target  "memory" 或 "user"
     * @param content 条目内容
     * @return 操作结果描述或错误信息
     */
    public synchronized Map<String, Object> add(String target, String content) {
        content = content.strip();
        if (content.isEmpty()) {
            return errorResult("Content cannot be empty.");
        }

        String scanError = threatDetector.scan(content);
        if (scanError != null) {
            return errorResult(scanError);
        }

        List<String> entries = entriesFor(target);
        if (entries == null) return errorResult("Invalid target '" + target + "'. Use 'memory' or 'user'.");

        if (entries.contains(content)) {
            return successResult(target, "Entry already exists (no duplicate added).");
        }

        int limit = charLimit(target);
        List<String> newEntries = new ArrayList<>(entries);
        newEntries.add(content);
        int newTotal = charCount(newEntries);

        if (newTotal > limit) {
            int current = charCount(entries);
            Map<String, Object> result = errorResult(
                String.format("Memory at %,d/%,d chars. Adding this entry (%d chars) would exceed the limit. Replace or remove existing entries first.",
                    current, limit, content.length()));
            result.put("current_entries", entries);
            result.put("usage", String.format("%,d/%,d", current, limit));
            return result;
        }

        entries.add(content);
        saveToDisk(target);
        return successResult(target, "Entry added.");
    }

    /**
     * 替换匹配的条目。
     *
     * @param target     "memory" 或 "user"
     * @param oldText    用于匹配现有条目的短文本
     * @param newContent 新的条目内容
     * @return 操作结果
     */
    public synchronized Map<String, Object> replace(String target, String oldText, String newContent) {
        oldText = oldText.strip();
        newContent = newContent.strip();
        if (oldText.isEmpty()) return errorResult("old_text cannot be empty.");
        if (newContent.isEmpty()) return errorResult("new_content cannot be empty. Use 'remove' to delete entries.");

        String scanError = threatDetector.scan(newContent);
        if (scanError != null) return errorResult(scanError);

        List<String> entries = entriesFor(target);
        if (entries == null) return errorResult("Invalid target '" + target + "'.");

        List<int[]> matches = findMatches(entries, oldText);
        if (matches.isEmpty()) return errorResult("No entry matched '" + oldText + "'.");
        if (matches.size() > 1) {
            // 检查是否是完全相同的重复条目
            Set<String> uniqueTexts = new java.util.HashSet<>();
            for (int[] m : matches) uniqueTexts.add(entries.get(m[0]));
            if (uniqueTexts.size() > 1) {
                return errorResult("Multiple entries matched '" + oldText + "'. Be more specific.");
            }
        }

        int idx = matches.get(0)[0];
        int limit = charLimit(target);
        List<String> testEntries = new ArrayList<>(entries);
        testEntries.set(idx, newContent);
        int newTotal = charCount(testEntries);

        if (newTotal > limit) {
            return errorResult(String.format(
                "Replacement would put memory at %,d/%,d chars. Shorten the new content or remove other entries first.",
                newTotal, limit));
        }

        entries.set(idx, newContent);
        saveToDisk(target);
        return successResult(target, "Entry replaced.");
    }

    /**
     * 删除匹配的条目。
     *
     * @param target  "memory" 或 "user"
     * @param oldText 用于匹配现有条目的短文本
     * @return 操作结果
     */
    public synchronized Map<String, Object> remove(String target, String oldText) {
        oldText = oldText.strip();
        if (oldText.isEmpty()) return errorResult("old_text cannot be empty.");

        List<String> entries = entriesFor(target);
        if (entries == null) return errorResult("Invalid target '" + target + "'.");

        List<int[]> matches = findMatches(entries, oldText);
        if (matches.isEmpty()) return errorResult("No entry matched '" + oldText + "'.");
        if (matches.size() > 1) {
            Set<String> uniqueTexts = new java.util.HashSet<>();
            for (int[] m : matches) uniqueTexts.add(entries.get(m[0]));
            if (uniqueTexts.size() > 1) {
                return errorResult("Multiple entries matched '" + oldText + "'. Be more specific.");
            }
        }

        entries.remove(matches.get(0)[0]);
        saveToDisk(target);
        return successResult(target, "Entry removed.");
    }

    /**
     * 获取用于系统提示注入的冻结快照。
     *
     * @param target "memory" 或 "user"
     * @return 快照文本，空字符串表示无内容
     */
    public String formatForSystemPrompt(String target) {
        String block = systemPromptSnapshot.getOrDefault(target, "");
        return block.isEmpty() ? null : block;
    }

    /**
     * 获取当前活跃的所有记忆条目。
     */
    public Map<String, List<String>> getAllEntries() {
        return Map.of("memory", List.copyOf(memoryEntries), "user", List.copyOf(userEntries));
    }

    /**
     * 清除指定目标的所有记忆。
     */
    public synchronized void clear(String target) {
        entriesFor(target).clear();
        saveToDisk(target);
        log.info("Memory cleared for target: {}", target);
    }

    // --- 内部方法 ---

    private List<String> entriesFor(String target) {
        return switch (target) {
            case "memory" -> memoryEntries;
            case "user" -> userEntries;
            default -> null;
        };
    }

    private int charLimit(String target) {
        return "user".equals(target) ? properties.getUserCharLimit() : properties.getMemoryCharLimit();
    }

    private static int charCount(List<String> entries) {
        if (entries.isEmpty()) return 0;
        return String.join(ENTRY_DELIMITER, entries).length();
    }

    private List<int[]> findMatches(List<String> entries, String oldText) {
        List<int[]> matches = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).contains(oldText)) {
                matches.add(new int[]{i});
            }
        }
        return matches;
    }

    private static String renderBlock(String target, List<String> entries) {
        if (entries.isEmpty()) return "";
        String content = String.join(ENTRY_DELIMITER, entries);
        int current = content.length();
        int limit = "user".equals(target) ? 1375 : 2200;  // approximate, will be set by properties
        int pct = Math.min(100, limit > 0 ? (int) ((current * 100L) / limit) : 0);

        String header = "user".equals(target)
            ? String.format("USER PROFILE (who the user is) [%d%% — %,d chars]", pct, current)
            : String.format("MEMORY (your personal notes) [%d%% — %,d chars]", pct, current);

        String separator = "═".repeat(46);
        return "%s\n%s\n%s\n%s".formatted(separator, header, separator, content);
    }

    private static List<String> readFile(Path path) {
        if (!Files.exists(path)) return List.of();
        try {
            String raw = Files.readString(path);
            if (raw.strip().isEmpty()) return List.of();
            List<String> entries = new ArrayList<>();
            for (String e : raw.split(ENTRY_DELIMITER, -1)) {
                String trimmed = e.strip();
                if (!trimmed.isEmpty()) entries.add(trimmed);
            }
            return entries;
        } catch (IOException e) {
            log.warn("Failed to read memory file {}: {}", path, e.getMessage());
            return List.of();
        }
    }

    private void saveToDisk(String target) {
        Path memDir = Path.of(properties.getHomeDir());
        ensureDir(memDir);
        Path path = memDir.resolve("user".equals(target) ? "USER.md" : "MEMORY.md");
        List<String> entries = entriesFor(target);
        String content = entries.isEmpty() ? "" : String.join(ENTRY_DELIMITER, entries);

        try {
            Path tmpFile = Files.createTempFile(memDir, ".mem_", ".tmp");
            Files.writeString(tmpFile, content);
            Files.move(tmpFile, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to write memory file {}: {}", path, e.getMessage());
        }
    }

    private static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("Failed to create memory directory {}: {}", dir, e.getMessage());
        }
    }

    private Map<String, Object> successResult(String target, String message) {
        List<String> entries = entriesFor(target);
        int current = charCount(entries);
        int limit = charLimit(target);
        int pct = Math.min(100, limit > 0 ? (int) ((current * 100L) / limit) : 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("target", target);
        result.put("entries", entries);
        result.put("usage", String.format("%d%% — %,d/%,d chars", pct, current, limit));
        result.put("entry_count", entries.size());
        result.put("message", message);
        return result;
    }

    private Map<String, Object> errorResult(String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", error);
        return result;
    }

    private static void deduplicate(List<String> entries) {
        // 保持顺序的去重
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>(entries);
        entries.clear();
        entries.addAll(seen);
    }
}
