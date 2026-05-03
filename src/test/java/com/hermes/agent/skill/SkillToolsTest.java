package com.hermes.agent.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillToolsTest {

    private SkillRegistry registry;
    private SkillManager manager;
    private SkillPreprocessor preprocessor;
    private SkillTools tools;

    @BeforeEach
    void setUp() {
        registry = mock(SkillRegistry.class);
        manager = mock(SkillManager.class);
        preprocessor = mock(SkillPreprocessor.class);
        tools = new SkillTools(registry, manager, preprocessor);
    }

    @Test
    void skillsListReturnsAll() {
        SkillDefinition s1 = new SkillDefinition("skill-a", "Skill A",
                List.of(), List.of("tag1"), List.of(), Path.of("/tmp/a"), Path.of("/tmp/a/SKILL.md"),
                "content", List.of());
        SkillDefinition s2 = new SkillDefinition("skill-b", "Skill B",
                List.of(), List.of("tag2"), List.of(), Path.of("/tmp/b"), Path.of("/tmp/b/SKILL.md"),
                "content", List.of());
        when(registry.getAllSkills()).thenReturn(List.of(s1, s2));
        when(registry.getCategories()).thenReturn(List.of("cat1"));

        String result = tools.skillsList(null);
        assertTrue(result.contains("skill-a"));
        assertTrue(result.contains("skill-b"));
        assertTrue(result.contains("\"count\": 2"));
    }

    @Test
    void skillViewReturnsContent() {
        SkillDefinition def = new SkillDefinition("my-skill", "My Skill",
                List.of(), List.of(), List.of(), Path.of("/tmp"), Path.of("/tmp/SKILL.md"),
                "# Original Content", List.of("refs/api.md"));
        when(registry.getSkill("my-skill")).thenReturn(Optional.of(def));
        when(preprocessor.preprocess("# Original Content", Path.of("/tmp"), "s1"))
                .thenReturn("# Processed Content");

        String result = tools.skillView("my-skill", null, "s1");
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("my-skill"));
        assertTrue(result.contains("content_length"));
    }

    @Test
    void skillViewNotFound() {
        when(registry.getSkill("nonexistent")).thenReturn(Optional.empty());
        String result = tools.skillView("nonexistent", null, "s1");
        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("not found"));
    }

    @Test
    void activateSkillSucceeds() {
        when(manager.activateSkill("s1", "my-skill")).thenReturn(true);
        String result = tools.activateSkill("my-skill", "s1");
        assertTrue(result.contains("\"success\": true"));
    }

    @Test
    void activateSkillFails() {
        when(manager.activateSkill("s1", "nonexistent")).thenReturn(false);
        String result = tools.activateSkill("nonexistent", "s1");
        assertTrue(result.contains("\"success\": false"));
    }

    @Test
    void deactivateSkillSucceeds() {
        when(manager.deactivateSkill("s1", "my-skill")).thenReturn(true);
        String result = tools.deactivateSkill("my-skill", "s1");
        assertTrue(result.contains("\"success\": true"));
    }

    @Test
    void deactivateSkillFails() {
        when(manager.deactivateSkill("s1", "unknown")).thenReturn(false);
        String result = tools.deactivateSkill("unknown", "s1");
        assertTrue(result.contains("\"success\": false"));
    }

    @Test
    void skillsListWithCategoryFilter() {
        SkillDefinition s1 = new SkillDefinition("skill-a", "Skill A",
                List.of(), List.of(), List.of(), Path.of("/tmp/a"), Path.of("/tmp/a/SKILL.md"),
                "content", List.of());
        when(registry.getSkillsByCategory("mlops")).thenReturn(List.of(s1));

        String result = tools.skillsList("mlops");
        assertTrue(result.contains("skill-a"));
    }
}
