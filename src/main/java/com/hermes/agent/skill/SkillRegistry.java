package com.hermes.agent.skill;

import com.hermes.agent.prompt.ContextFileDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 技能注册中心。
 * 扫描 HERMES_HOME/skills/ 及外部目录下的 SKILL.md 文件，
 * 解析 YAML frontmatter，注册所有可用技能。
 */
@Component
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private static final Pattern FRONTMATTER = Pattern.compile("^---\\s*\\n([\\s\\S]*?)\\n---\\s*\\n([\\s\\S]*)$");
    private static final Pattern TAGS_BRACKET = Pattern.compile("^\\[(.+)]$");
    private static final Set<String> EXCLUDED_DIRS = Set.of(".git", ".github", ".hub");

    private final SkillProperties properties;
    private final Path skillsDir;
    private final Map<String, SkillDefinition> registry = new ConcurrentHashMap<>();

    public SkillRegistry(SkillProperties properties) {
        this(properties, ContextFileDiscovery.resolveHermesHome().resolve("skills"));
    }

    /**
     * 可指定技能目录的构造函数（用于测试隔离）。
     */
    SkillRegistry(SkillProperties properties, Path skillsDir) {
        this.properties = properties;
        this.skillsDir = skillsDir;
        scanAllSkills();
        log.info("SkillRegistry initialized: {} skills found", registry.size());
    }

    /**
     * 默认构造函数（Spring 自动配置需要）。
     */
    public SkillRegistry() {
        this(new SkillProperties());
    }

    /**
     * 扫描所有技能目录，注册可用技能。
     */
    public void scanAllSkills() {
        registry.clear();
        List<Path> dirsToScan = new ArrayList<>();
        if (skillsDir.toFile().exists()) {
            dirsToScan.add(skillsDir);
        }
        for (String extDir : properties.getExternalDirs()) {
            Path p = Path.of(extDir);
            if (p.toFile().exists()) {
                dirsToScan.add(p);
            }
        }
        Set<String> seenNames = new HashSet<>();
        for (Path dir : dirsToScan) {
            try (Stream<Path> walk = Files.walk(dir)) {
                List<Path> skillFiles = walk
                        .filter(Files::isRegularFile)
                        .filter(p -> "SKILL.md".equals(p.getFileName().toString()))
                        .filter(this::notExcluded)
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();

                for (Path skillMd : skillFiles) {
                    try {
                        SkillDefinition def = parseSkillMd(skillMd);
                        if (def == null) continue;
                        if (!def.isPlatformCompatible()) continue;
                        if (isDisabled(def.name())) continue;
                        if (seenNames.contains(def.name())) continue;
                        seenNames.add(def.name());
                        registry.put(def.name(), def);
                    } catch (Exception e) {
                        log.warn("Failed to parse skill at {}: {}", skillMd, e.getMessage());
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to scan skills directory {}: {}", dir, e.getMessage());
            }
        }
    }

    private boolean notExcluded(Path p) {
        for (String part : p.toString().split(Path.of("").getFileSystem().getSeparator().isEmpty() ? "/" : Path.of("").getFileSystem().getSeparator())) {
            if (EXCLUDED_DIRS.contains(part)) return false;
        }
        // Check each name component
        Path current = p;
        while (current != null) {
            String name = current.getFileName() != null ? current.getFileName().toString() : "";
            if (EXCLUDED_DIRS.contains(name)) return false;
            current = current.getParent();
        }
        return true;
    }

    /**
     * 解析 SKILL.md 文件，提取 frontmatter 和内容。
     */
    private SkillDefinition parseSkillMd(Path skillMd) throws IOException {
        String content = Files.readString(skillMd);
        Matcher m = FRONTMATTER.matcher(content);
        if (!m.find()) {
            return null;
        }
        String yamlBlock = m.group(1);
        String body = m.group(2);

        Map<String, String> frontmatter = parseYamlBlock(yamlBlock);

        String name = frontmatter.getOrDefault("name", skillMd.getParent().getFileName().toString());
        String description = frontmatter.getOrDefault("description", extractFirstLine(body));
        List<String> platforms = parseListValue(frontmatter.get("platforms"));
        List<String> tags = parseListValue(frontmatter.get("tags"));
        List<String> relatedSkills = parseListValue(frontmatter.get("related_skills"));
        List<String> linkedFiles = findLinkedFiles(skillMd.getParent());

        return new SkillDefinition.Builder()
                .name(name)
                .description(description)
                .platforms(platforms)
                .tags(tags)
                .relatedSkills(relatedSkills)
                .skillDir(skillMd.getParent())
                .skillMdPath(skillMd)
                .content(content)
                .linkedFiles(linkedFiles)
                .build();
    }

    private Map<String, String> parseYamlBlock(String yaml) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : yaml.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || !line.contains(":")) continue;
            int colonIdx = line.indexOf(':');
            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();
            result.put(key, value);
        }
        return result;
    }

    private List<String> parseListValue(String raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        if (raw.startsWith("[") && raw.endsWith("]")) {
            String inner = raw.substring(1, raw.length() - 1).trim();
            if (inner.isEmpty()) return List.of();
            return Arrays.stream(inner.split(","))
                    .map(String::trim)
                    .map(s -> s.replaceAll("^['\"]|['\"]$", ""))
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return List.of(raw);
    }

    private String extractFirstLine(String body) {
        for (String line : body.split("\n")) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                return line.length() > 100 ? line.substring(0, 100) + "..." : line;
            }
        }
        return "";
    }

    private List<String> findLinkedFiles(Path skillDir) {
        List<String> files = new ArrayList<>();
        String[] subdirs = {"references", "templates", "scripts", "assets"};
        for (String subdir : subdirs) {
            Path d = skillDir.resolve(subdir);
            if (d.toFile().isDirectory()) {
                try (Stream<Path> walk = Files.walk(d)) {
                    walk.filter(Files::isRegularFile)
                            .forEach(p -> {
                                try {
                                    files.add(skillDir.relativize(p).toString());
                                } catch (Exception ignored) {}
                            });
                } catch (IOException ignored) {}
            }
        }
        return files;
    }

    private boolean isDisabled(String name) {
        Set<String> disabled = new HashSet<>(properties.getDisabled());
        String os = System.getProperty("os.name").toLowerCase();
        String platform = os.contains("mac") ? "macos" : os.contains("linux") ? "linux" : os.contains("win") ? "windows" : null;
        if (platform != null && properties.getPlatformDisabled().containsKey(platform)) {
            disabled.addAll(properties.getPlatformDisabled().get(platform));
        }
        return disabled.contains(name);
    }

    /**
     * 获取所有已注册技能（按名称排序）。
     */
    public List<SkillDefinition> getAllSkills() {
        return registry.values().stream()
                .sorted(Comparator.comparing(SkillDefinition::name))
                .toList();
    }

    /**
     * 按名称获取技能。
     */
    public Optional<SkillDefinition> getSkill(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    /**
     * 获取技能名称列表。
     */
    public Set<String> getSkillNames() {
        return Set.copyOf(registry.keySet());
    }

    /**
     * 获取所有分类。
     */
    public List<String> getCategories() {
        return getAllSkills().stream()
                .map(s -> {
                    try {
                        Path rel = skillsDir.relativize(s.skillDir());
                        if (rel.getNameCount() > 1) {
                            return rel.getName(0).toString();
                        }
                    } catch (Exception ignored) {}
                    return "other";
                })
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 按分类过滤技能。
     */
    public List<SkillDefinition> getSkillsByCategory(String category) {
        return getAllSkills().stream()
                .filter(s -> {
                    try {
                        Path rel = skillsDir.relativize(s.skillDir());
                        if (rel.getNameCount() > 1) {
                            return rel.getName(0).toString().equals(category);
                        }
                    } catch (Exception ignored) {}
                    return false;
                })
                .toList();
    }
}
