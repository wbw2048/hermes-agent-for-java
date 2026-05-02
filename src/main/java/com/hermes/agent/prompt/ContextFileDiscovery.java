package com.hermes.agent.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * 上下文文件发现。
 * <p>
 * 按会话维度搜索上下文文件，每个会话拥有独立的上下文空间。
 * 搜索路径：~/.hermes/contexts/&lt;session-id&gt;/ → SOUL.md（全局）。
 * <p>
 * 优先级（首个匹配胜出，仅加载一种项目上下文）：
 * <ol>
 *   <li>.hermes.md / HERMES.md</li>
 *   <li>AGENTS.md / agents.md</li>
 *   <li>CLAUDE.md / claude.md</li>
 *   <li>.cursorrules + .cursor/rules/*.mdc</li>
 * </ol>
 * SOUL.md 从 HERMES_HOME 全局目录加载，独立于会话上下文。
 */
public class ContextFileDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ContextFileDiscovery.class);
    private static final PromptInjectionDetector detector = new PromptInjectionDetector();
    private static final ContextFileTruncator truncator = new ContextFileTruncator();

    private static final String CONTEXTS_DIR = "contexts";

    private ContextFileDiscovery() {}

    // ========== Session Context Directory ==========

    /**
     * 解析会话上下文目录：HERMES_HOME/contexts/&lt;session-id&gt;/
     * 若目录不存在且 createIfMissing 为 true，则创建。
     */
    public static Path resolveSessionContextDir(String sessionId, boolean createIfMissing) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        Path hermesHome = resolveHermesHome();
        Path sessionDir = hermesHome.resolve(CONTEXTS_DIR).resolve(sessionId);
        if (createIfMissing && !Files.isDirectory(sessionDir)) {
            try {
                Files.createDirectories(sessionDir);
            } catch (IOException e) {
                log.warn("Could not create session context dir: {}", sessionDir);
                return null;
            }
        }
        return sessionDir;
    }

    // ========== Single-Directory Loaders ==========

    /**
     * 在指定目录中按优先级查找 .hermes.md / HERMES.md。
     */
    static String loadHermesMdFromDir(Path dir) {
        for (String name : new String[]{".hermes.md", "HERMES.md"}) {
            Path candidate = dir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return readFile(candidate, name, dir);
            }
        }
        return "";
    }

    /**
     * 在指定目录中查找 AGENTS.md / agents.md。
     */
    static String loadAgentsMdFromDir(Path dir) {
        for (String name : new String[]{"AGENTS.md", "agents.md"}) {
            Path candidate = dir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return readFile(candidate, name, dir);
            }
        }
        return "";
    }

    /**
     * 在指定目录中查找 CLAUDE.md / claude.md。
     */
    static String loadClaudeMdFromDir(Path dir) {
        for (String name : new String[]{"CLAUDE.md", "claude.md"}) {
            Path candidate = dir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return readFile(candidate, name, dir);
            }
        }
        return "";
    }

    /**
     * 在指定目录中查找 .cursorrules + .cursor/rules/*.mdc。
     */
    static String loadCursorRulesFromDir(Path dir) {
        StringBuilder sb = new StringBuilder();

        Path cursorrules = dir.resolve(".cursorrules");
        if (Files.isRegularFile(cursorrules)) {
            String content = readRawFile(cursorrules, ".cursorrules", dir);
            if (!content.isEmpty()) {
                sb.append("## .cursorrules\n\n").append(content).append("\n\n");
            }
        }

        Path cursorRulesDir = dir.resolve(".cursor").resolve("rules");
        if (Files.isDirectory(cursorRulesDir)) {
            try (var stream = Files.list(cursorRulesDir)) {
                stream.filter(p -> p.toString().endsWith(".mdc"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(mdcFile -> {
                        String content = readRawFile(mdcFile, ".cursor/rules/" + mdcFile.getFileName(), dir);
                        if (!content.isEmpty()) {
                            sb.append("## .cursor/rules/").append(mdcFile.getFileName()).append("\n\n").append(content).append("\n\n");
                        }
                    });
            } catch (IOException e) {
                log.debug("Could not list .cursor/rules/: {}", e.getMessage());
            }
        }

        if (sb.isEmpty()) {
            return "";
        }
        return truncator.truncate(sb.toString(), ".cursorrules");
    }

    // ========== SOUL.md ==========

    /**
     * 从 HERMES_HOME/SOUL.md 加载智能体身份。
     * HERMES_HOME 优先级：环境变量 → ~/.hermes
     */
    public static String loadSoulMd() {
        return loadSoulMdFrom(resolveHermesHome());
    }

    /**
     * 从指定路径加载 SOUL.md（用于测试）。
     */
    static String loadSoulMdFrom(Path hermesHome) {
        if (hermesHome == null) {
            return "";
        }
        Path soulPath = hermesHome.resolve("SOUL.md");
        if (!Files.isRegularFile(soulPath)) {
            return "";
        }
        return readFile(soulPath, "SOUL.md", soulPath);
    }

    /**
     * 解析 HERMES_HOME 路径。
     * 优先级：HERMES_HOME 环境变量 → ~/.hermes
     */
    public static Path resolveHermesHome() {
        String hermesHome = System.getenv("HERMES_HOME");
        if (hermesHome != null && !hermesHome.isEmpty()) {
            return Path.of(hermesHome);
        }
        return Path.of(System.getProperty("user.home")).resolve(".hermes");
    }

    // ========== YAML Frontmatter Strip ==========

    /**
     * 移除 YAML frontmatter（--- 分隔块）。
     */
    static String stripYamlFrontmatter(String content) {
        if (!content.startsWith("---")) {
            return content;
        }
        int end = content.indexOf("\n---", 3);
        if (end != -1) {
            String body = content.substring(end + 4).stripLeading();
            return body.isEmpty() ? content : body;
        }
        return content;
    }

    // ========== Main Entry Point ==========

    /**
     * 基于会话发现并加载上下文文件（测试用，支持自定义 HERMES_HOME）。
     *
     * @param sessionId        会话 ID
     * @param hermesHomeOverride 自定义 HERMES_HOME 路径
     * @return 组合后的上下文提示文本
     */
    static String buildContextFilesPrompt(String sessionId, Path hermesHomeOverride) {
        if (sessionId == null || sessionId.isBlank()) {
            String soulMd = loadSoulMdFrom(hermesHomeOverride);
            return soulMd.isEmpty() ? "" : "# Project Context\n\n" + soulMd;
        }

        Path sessionDir = hermesHomeOverride.resolve(CONTEXTS_DIR).resolve(sessionId);
        if (Files.isDirectory(sessionDir)) {
            String sessionContext = loadSessionContext(sessionDir);
            if (!sessionContext.isEmpty()) {
                String soulMd = loadSoulMdFrom(hermesHomeOverride);
                if (!soulMd.isEmpty()) {
                    return sessionContext + "\n\n" + soulMd;
                }
                return sessionContext;
            }
        }

        String soulMd = loadSoulMdFrom(hermesHomeOverride);
        return soulMd.isEmpty() ? "" : "# Project Context\n\n" + soulMd;
    }

    /**
     * 基于会话发现并加载上下文文件。
     *
     * @param sessionId 会话 ID，为空时仅加载 SOUL.md
     * @return 组合后的上下文提示文本，无上下文文件时返回空字符串
     */
    public static String buildContextFilesPrompt(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            String soulMd = loadSoulMd();
            return soulMd.isEmpty() ? "" : "# Project Context\n\n" + soulMd;
        }

        // 尝试从会话目录加载
        Path sessionDir = resolveSessionContextDir(sessionId, false);
        if (sessionDir != null && Files.isDirectory(sessionDir)) {
            String sessionContext = loadSessionContext(sessionDir);
            if (!sessionContext.isEmpty()) {
                // 会话级上下文 + SOUL.md
                String soulMd = loadSoulMd();
                if (!soulMd.isEmpty()) {
                    return sessionContext + "\n\n" + soulMd;
                }
                return sessionContext;
            }
        }

        // 会话目录无上下文文件：仅加载 SOUL.md
        String soulMd = loadSoulMd();
        return soulMd.isEmpty() ? "" : "# Project Context\n\n" + soulMd;
    }

    /**
     * 从会话目录加载所有上下文文件（first match wins）。
     */
    private static String loadSessionContext(Path sessionDir) {
        // 优先级：.hermes.md → AGENTS.md → CLAUDE.md → .cursorrules
        String projectContext = loadHermesMdFromDir(sessionDir);
        if (projectContext.isEmpty()) {
            projectContext = loadAgentsMdFromDir(sessionDir);
        }
        if (projectContext.isEmpty()) {
            projectContext = loadClaudeMdFromDir(sessionDir);
        }
        if (projectContext.isEmpty()) {
            projectContext = loadCursorRulesFromDir(sessionDir);
        }

        if (projectContext.isEmpty()) {
            return "";
        }
        return "# Project Context\n\nThe following project context files have been loaded and should be followed:\n\n" + projectContext;
    }

    // ========== Helpers ==========

    private static String readFile(Path path, String name, Path baseDir) {
        String content = readRawFile(path, name, baseDir);
        if (content.isEmpty()) {
            return "";
        }
        String result = truncator.truncate(content, name);
        return "## " + name + "\n\n" + result;
    }

    private static String readRawFile(Path path, String name, Path baseDir) {
        try {
            String content = Files.readString(path).strip();
            if (content.isEmpty()) {
                return "";
            }
            String relName = computeRelativeName(path, baseDir);
            content = stripYamlFrontmatter(content);
            content = detector.scanAndSanitize(content, relName);
            return content;
        } catch (IOException e) {
            log.debug("Could not read {}: {}", path, e.getMessage());
            return "";
        }
    }

    private static String computeRelativeName(Path path, Path baseDir) {
        try {
            return baseDir.relativize(path).toString();
        } catch (IllegalArgumentException e) {
            return path.getFileName().toString();
        }
    }
}
