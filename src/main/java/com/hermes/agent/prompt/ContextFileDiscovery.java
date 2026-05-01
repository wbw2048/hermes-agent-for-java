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
 * 按优先级搜索项目上下文文件，加载内容并做注入防护扫描和截断后拼入系统提示。
 * 优先级（首个匹配胜出，仅加载一种项目上下文）：
 * <ol>
 *   <li>.hermes.md / HERMES.md（从 cwd 向上搜索到 git 根）</li>
 *   <li>AGENTS.md / agents.md（仅 cwd）</li>
 *   <li>CLAUDE.md / claude.md（仅 cwd）</li>
 *   <li>.cursorrules + .cursor/rules/*.mdc（仅 cwd）</li>
 * </ol>
 * SOUL.md 从 HERMES_HOME 加载，独立于项目上下文。
 */
public class ContextFileDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ContextFileDiscovery.class);
    private static final PromptInjectionDetector detector = new PromptInjectionDetector();
    private static final ContextFileTruncator truncator = new ContextFileTruncator();

    private ContextFileDiscovery() {}

    // ========== Git Root Discovery ==========

    /**
     * 从起点逐层向上查找 .git 目录，返回包含 .git 的目录。
     */
    static Path findGitRoot(Path start) {
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    // ========== .hermes.md / HERMES.md ==========

    /**
     * 在 cwd → git root 范围内查找 .hermes.md 或 HERMES.md。
     */
    static String loadHermesMd(Path cwd) {
        Path gitRoot = findGitRoot(cwd);
        Path current = cwd.toAbsolutePath().normalize();

        while (true) {
            for (String name : new String[]{".hermes.md", "HERMES.md"}) {
                Path candidate = current.resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return readFile(candidate, name, cwd);
                }
            }
            if (gitRoot != null && current.equals(gitRoot)) {
                break;
            }
            Path parent = current.getParent();
            if (parent == null || parent.equals(current)) {
                break;
            }
            current = parent;
        }
        return "";
    }

    // ========== AGENTS.md ==========

    static String loadAgentsMd(Path cwd) {
        for (String name : new String[]{"AGENTS.md", "agents.md"}) {
            Path candidate = cwd.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return readFile(candidate, name, cwd);
            }
        }
        return "";
    }

    // ========== CLAUDE.md ==========

    static String loadClaudeMd(Path cwd) {
        for (String name : new String[]{"CLAUDE.md", "claude.md"}) {
            Path candidate = cwd.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return readFile(candidate, name, cwd);
            }
        }
        return "";
    }

    // ========== .cursorrules + .cursor/rules/*.mdc ==========

    static String loadCursorRules(Path cwd) {
        StringBuilder sb = new StringBuilder();

        Path cursorrules = cwd.resolve(".cursorrules");
        if (Files.isRegularFile(cursorrules)) {
            String content = readRawFile(cursorrules, ".cursorrules", cwd);
            if (!content.isEmpty()) {
                sb.append("## .cursorrules\n\n").append(content).append("\n\n");
            }
        }

        Path cursorRulesDir = cwd.resolve(".cursor").resolve("rules");
        if (Files.isDirectory(cursorRulesDir)) {
            try (var stream = Files.list(cursorRulesDir)) {
                stream.filter(p -> p.toString().endsWith(".mdc"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(mdcFile -> {
                        String content = readRawFile(mdcFile, ".cursor/rules/" + mdcFile.getFileName(), cwd);
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
     * 发现并加载上下文文件，返回组合后的项目上下文字符串。
     *
     * @param cwd 当前工作目录
     * @return 项目上下文提示文本，无上下文文件时返回空字符串
     */
    public static String buildContextFilesPrompt(Path cwd) {
        Path cwdPath = cwd.toAbsolutePath().normalize();
        StringBuilder sections = new StringBuilder();

        // 优先级：first match wins
        String projectContext = loadHermesMd(cwdPath);
        if (projectContext.isEmpty()) {
            projectContext = loadAgentsMd(cwdPath);
        }
        if (projectContext.isEmpty()) {
            projectContext = loadClaudeMd(cwdPath);
        }
        if (projectContext.isEmpty()) {
            projectContext = loadCursorRules(cwdPath);
        }

        if (!projectContext.isEmpty()) {
            sections.append(projectContext);
        }

        // SOUL.md 独立加载
        String soulMd = loadSoulMd();
        if (!soulMd.isEmpty()) {
            if (!sections.isEmpty()) {
                sections.append("\n\n");
            }
            sections.append(soulMd);
        }

        if (sections.isEmpty()) {
            return "";
        }
        return "# Project Context\n\nThe following project context files have been loaded and should be followed:\n\n" + sections;
    }

    // ========== Helpers ==========

    private static String readFile(Path path, String name, Path cwd) {
        String content = readRawFile(path, name, cwd);
        if (content.isEmpty()) {
            return "";
        }
        String result = truncator.truncate(content, name);
        return "## " + name + "\n\n" + result;
    }

    private static String readRawFile(Path path, String name, Path cwd) {
        try {
            String content = Files.readString(path).strip();
            if (content.isEmpty()) {
                return "";
            }
            String relName = computeRelativeName(path, cwd);
            content = stripYamlFrontmatter(content);
            content = detector.scanAndSanitize(content, relName);
            return content;
        } catch (IOException e) {
            log.debug("Could not read {}: {}", path, e.getMessage());
            return "";
        }
    }

    private static String computeRelativeName(Path path, Path cwd) {
        try {
            return cwd.relativize(path).toString();
        } catch (IllegalArgumentException e) {
            return path.getFileName().toString();
        }
    }
}
