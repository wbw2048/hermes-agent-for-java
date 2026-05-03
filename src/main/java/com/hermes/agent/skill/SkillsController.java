package com.hermes.agent.skill;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.*;

/**
 * REST API — 技能管理。
 * 提供技能列表、查看、激活/停用等管理接口。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillsController {

    private final SkillRegistry registry;
    private final SkillManager manager;

    public SkillsController(SkillRegistry registry, SkillManager manager) {
        this.registry = registry;
        this.manager = manager;
    }

    /**
     * 获取所有可用技能列表。
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listSkills(
            @RequestParam(required = false) String category
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

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("skills", result);
        body.put("categories", registry.getCategories());
        body.put("count", skills.size());
        return ResponseEntity.ok(body);
    }

    /**
     * 查看技能的详细信息（不含完整内容，仅元数据）。
     */
    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> getSkill(@PathVariable String name) {
        return registry.getSkill(name)
                .map(skill -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("success", true);
                    body.put("name", skill.name());
                    body.put("description", skill.description());
                    body.put("category", getCategory(skill));
                    body.put("tags", skill.tags());
                    body.put("linked_files", skill.linkedFiles());
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 为指定会话激活技能。
     */
    @PostMapping("/{name}/activate")
    public ResponseEntity<Map<String, Object>> activateSkill(
            @PathVariable String name,
            @RequestParam String sessionId
    ) {
        boolean ok = manager.activateSkill(sessionId, name);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", ok);
        body.put("message", ok
                ? "Skill '" + name + "' activated for session " + sessionId
                : "Skill '" + name + "' not found");
        return ResponseEntity.ok(body);
    }

    /**
     * 为指定会话停用技能。
     */
    @PostMapping("/{name}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateSkill(
            @PathVariable String name,
            @RequestParam String sessionId
    ) {
        boolean ok = manager.deactivateSkill(sessionId, name);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", ok);
        body.put("message", ok
                ? "Skill '" + name + "' deactivated"
                : "Skill '" + name + "' is not active for session " + sessionId);
        return ResponseEntity.ok(body);
    }

    /**
     * 获取指定会话的已激活技能列表。
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveSkills(
            @RequestParam String sessionId
    ) {
        Set<String> active = manager.getActiveSkills(sessionId);
        Set<String> global = manager.getGlobalActiveSkills();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("session_skills", active);
        body.put("global_skills", global);
        return ResponseEntity.ok(body);
    }

    private String getCategory(SkillDefinition skill) {
        try {
            Path skillsRoot = skill.skillMdPath();
            // Walk up to find the skills directory
            while (skillsRoot != null) {
                String name = skillsRoot.getFileName() != null ? skillsRoot.getFileName().toString() : "";
                if ("skills".equals(name)) {
                    // Relativize from the skills directory itself to get the category
                    Path rel = skillsRoot.relativize(skill.skillDir());
                    if (rel.getNameCount() > 0) {
                        return rel.getName(0).toString();
                    }
                    break;
                }
                skillsRoot = skillsRoot.getParent();
            }
        } catch (Exception ignored) {}
        return "other";
    }
}
