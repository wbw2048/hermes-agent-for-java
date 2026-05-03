package com.hermes.agent.skill;

import java.nio.file.Path;
import java.util.List;

/**
 * 技能元数据定义。
 * 从 SKILL.md 文件的 YAML frontmatter 解析而来。
 */
public record SkillDefinition(
        String name,
        String description,
        List<String> platforms,
        List<String> tags,
        List<String> relatedSkills,
        Path skillDir,
        Path skillMdPath,
        String content,
        List<String> linkedFiles
) {
    public boolean isPlatformCompatible() {
        if (platforms == null || platforms.isEmpty()) {
            return true;
        }
        String os = System.getProperty("os.name").toLowerCase();
        for (String p : platforms) {
            String normalized = p.toLowerCase().trim();
            if (normalized.equals("macos") && os.contains("mac")) return true;
            if (normalized.equals("linux") && os.contains("linux")) return true;
            if (normalized.equals("windows") && os.contains("win")) return true;
        }
        return false;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private List<String> platforms = List.of();
        private List<String> tags = List.of();
        private List<String> relatedSkills = List.of();
        private Path skillDir;
        private Path skillMdPath;
        private String content;
        private List<String> linkedFiles = List.of();

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String desc) { this.description = desc; return this; }
        public Builder platforms(List<String> p) { this.platforms = p; return this; }
        public Builder tags(List<String> t) { this.tags = t; return this; }
        public Builder relatedSkills(List<String> r) { this.relatedSkills = r; return this; }
        public Builder skillDir(Path d) { this.skillDir = d; return this; }
        public Builder skillMdPath(Path p) { this.skillMdPath = p; return this; }
        public Builder content(String c) { this.content = c; return this; }
        public Builder linkedFiles(List<String> f) { this.linkedFiles = f; return this; }

        public SkillDefinition build() {
            return new SkillDefinition(name, description, platforms, tags, relatedSkills,
                    skillDir, skillMdPath, content, linkedFiles);
        }
    }
}
