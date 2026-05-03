package com.hermes.agent.skill;

import com.hermes.agent.tool.annotation.ToolSet;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 供 LLM 调用的技能管理工具。
 * 包含 skills_list、skill_view、activate_skill、deactivate_skill 四个工具方法。
 */
@Component
@ToolSet("skills")
public class SkillTools {

    private final SkillRegistry registry;
    private final SkillManager manager;
    private final SkillPreprocessor preprocessor;

    public SkillTools(SkillRegistry registry, SkillManager manager, SkillPreprocessor preprocessor) {
        this.registry = registry;
        this.manager = manager;
        this.preprocessor = preprocessor;
    }

    /**
     * 列出所有可用技能（仅含元数据，token 高效）。
     *
     * @param category 可选分类过滤
     * @return JSON 格式的技能列表
     */
    @Tool(description = "List available skills (name + description). Use skill_view(name) to load full content.")
    public String skillsList(
            @ToolParam(description = "Optional category filter") String category
    ) {
        List<SkillDefinition> skills = category != null && !category.isBlank()
                ? registry.getSkillsByCategory(category)
                : registry.getAllSkills();

        List<Map<String, String>> result = skills.stream()
                .map(s -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("name", s.name());
                    m.put("description", s.description());
                    m.put("category", getCategory(s));
                    return m;
                })
                .toList();

        return buildJson(true, result, registry.getCategories(),
                skills.size() + " skills available");
    }

    /**
     * 查看技能的完整内容或指定文件。
     *
     * @param name     技能名称
     * @param filePath 可选的相对路径（查看技能目录下的特定文件）
     * @param sessionId 当前会话 ID（用于预处理）
     * @return JSON 格式的技能内容
     */
    @Tool(description = "View a skill's full content or a specific file within the skill directory. First call returns SKILL.md content plus linked files. To access linked files, call again with file_path parameter.")
    public String skillView(
            @ToolParam(description = "The skill name (use skills_list to see available skills)") String name,
            @ToolParam(description = "Optional relative path within the skill directory (e.g., 'references/api.md')") String filePath,
            @ToolParam(description = "Current session ID") String sessionId
    ) {
        Optional<SkillDefinition> opt = registry.getSkill(name);
        if (opt.isEmpty()) {
            List<String> available = registry.getSkillNames().stream()
                    .sorted().limit(20).toList();
            return buildErrorJson("Skill '" + name + "' not found", available);
        }

        SkillDefinition skill = opt.get();

        if (filePath != null && !filePath.isBlank()) {
            return viewSkillFile(skill, filePath);
        }

        String processedContent = preprocessor.preprocess(skill.content(), skill.skillDir(), sessionId);
        List<String> linkedFiles = skill.linkedFiles();

        return buildViewJson(skill, processedContent, linkedFiles);
    }

    /**
     * 为当前会话激活指定技能。
     */
    @Tool(description = "Activate a skill for the current session. The skill's instructions will be added to your system prompt.")
    public String activateSkill(
            @ToolParam(description = "The skill name to activate") String name,
            @ToolParam(description = "Current session ID") String sessionId
    ) {
        boolean ok = manager.activateSkill(sessionId, name);
        if (ok) {
            return "{\"success\": true, \"message\": \"Skill '" + name + "' activated for this session\"}";
        }
        return "{\"success\": false, \"error\": \"Skill '" + name + "' not found\"}";
    }

    /**
     * 为当前会话停用指定技能。
     */
    @Tool(description = "Deactivate a previously activated skill for the current session.")
    public String deactivateSkill(
            @ToolParam(description = "The skill name to deactivate") String name,
            @ToolParam(description = "Current session ID") String sessionId
    ) {
        boolean ok = manager.deactivateSkill(sessionId, name);
        if (ok) {
            return "{\"success\": true, \"message\": \"Skill '" + name + "' deactivated\"}";
        }
        return "{\"success\": false, \"error\": \"Skill '" + name + "' is not active for this session\"}";
    }

    // --- Private helpers ---

    private String getCategory(SkillDefinition skill) {
        try {
            Path rel = Path.of(System.getProperty("user.home"), ".hermes", "skills")
                    .relativize(skill.skillDir());
            if (rel.getNameCount() > 1) {
                return rel.getName(0).toString();
            }
        } catch (Exception ignored) {}
        return "other";
    }

    private String viewSkillFile(SkillDefinition skill, String filePath) {
        Path target = skill.skillDir().resolve(filePath);
        if (!Files.exists(target)) {
            return "{\"success\": false, \"error\": \"File '" + filePath + "' not found in skill '" + skill.name() + "'\"}";
        }
        if (!target.startsWith(skill.skillDir())) {
            return "{\"success\": false, \"error\": \"Path traversal not allowed\"}";
        }
        try {
            String content = Files.readString(target);
            return "{\"success\": true, \"name\": \"" + skill.name() + "\", \"file\": \"" + filePath + "\", \"content_length\": " + content.length() + "}";
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"Failed to read file: " + e.getMessage() + "\"}";
        }
    }

    private String buildJson(boolean success, List<Map<String, String>> skills,
                              List<String> categories, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\": ").append(success);
        sb.append(", \"skills\": [");
        for (int i = 0; i < skills.size(); i++) {
            if (i > 0) sb.append(", ");
            Map<String, String> s = skills.get(i);
            sb.append("{\"name\": \"").append(escapeJson(s.get("name")));
            sb.append("\", \"description\": \"").append(escapeJson(s.get("description")));
            sb.append("\", \"category\": \"").append(escapeJson(s.get("category"))).append("\"}");
        }
        sb.append("], \"categories\": ");
        sb.append(categories);
        sb.append(", \"count\": ").append(skills.size());
        sb.append(", \"hint\": \"Use skill_view(name) to see full content\"}");
        return sb.toString();
    }

    private String buildViewJson(SkillDefinition skill, String content, List<String> linkedFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\": true");
        sb.append(", \"name\": \"").append(escapeJson(skill.name())).append("\"");
        sb.append(", \"description\": \"").append(escapeJson(skill.description())).append("\"");
        sb.append(", \"content\": ").append("\"...[content loaded]...\"");
        sb.append(", \"content_length\": ").append(content.length());
        sb.append(", \"linked_files\": ");
        if (linkedFiles != null && !linkedFiles.isEmpty()) {
            sb.append(linkedFiles);
        } else {
            sb.append("null");
        }
        sb.append(", \"tags\": ").append(skill.tags() != null ? skill.tags() : "[]");
        sb.append("}");
        return sb.toString();
    }

    private String buildErrorJson(String error, List<String> suggestions) {
        return "{\"success\": false, \"error\": \"" + escapeJson(error)
                + "\", \"available_skills\": " + suggestions + "}";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }
}
