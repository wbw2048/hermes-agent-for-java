package com.hermes.agent.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillManagerTest {

    private SkillRegistry registry;
    private SkillPreprocessor preprocessor;
    private SkillManager manager;

    @BeforeEach
    void setUp() {
        registry = mock(SkillRegistry.class);
        preprocessor = mock(SkillPreprocessor.class);
        manager = new SkillManager(registry, preprocessor);
    }

    @Test
    void activateSkillSucceeds() {
        when(registry.getSkill("git-workflow")).thenReturn(
                java.util.Optional.of(new SkillDefinition("git-workflow", "Git workflow",
                        List.of(), List.of(), List.of(), Path.of("/tmp"), Path.of("/tmp/SKILL.md"),
                        "content", List.of()))
        );

        assertTrue(manager.activateSkill("session-1", "git-workflow"));
        assertTrue(manager.getActiveSkills("session-1").contains("git-workflow"));
    }

    @Test
    void activateNonexistentSkillFails() {
        when(registry.getSkill("unknown")).thenReturn(java.util.Optional.empty());
        assertFalse(manager.activateSkill("session-1", "unknown"));
    }

    @Test
    void deactivateSkillSucceeds() {
        when(registry.getSkill("git-workflow")).thenReturn(
                java.util.Optional.of(new SkillDefinition("git-workflow", "Git workflow",
                        List.of(), List.of(), List.of(), Path.of("/tmp"), Path.of("/tmp/SKILL.md"),
                        "content", List.of()))
        );
        manager.activateSkill("session-1", "git-workflow");

        assertTrue(manager.deactivateSkill("session-1", "git-workflow"));
        assertFalse(manager.getActiveSkills("session-1").contains("git-workflow"));
    }

    @Test
    void deactivateInactiveSkillFails() {
        assertFalse(manager.deactivateSkill("session-1", "unknown"));
    }

    @Test
    void globalActiveSkillsApplyToAllSessions() {
        when(registry.getSkill("global-skill")).thenReturn(
                java.util.Optional.of(new SkillDefinition("global-skill", "Global",
                        List.of(), List.of(), List.of(), Path.of("/tmp"), Path.of("/tmp/SKILL.md"),
                        "content", List.of()))
        );

        manager.activateGlobalSkill("global-skill");

        assertTrue(manager.getActiveSkills("session-1").contains("global-skill"));
        assertTrue(manager.getActiveSkills("session-2").contains("global-skill"));
    }

    @Test
    void clearSessionSkillsRemovesAll() {
        when(registry.getSkill("skill-a")).thenReturn(
                java.util.Optional.of(new SkillDefinition("skill-a", "A",
                        List.of(), List.of(), List.of(), Path.of("/tmp"), Path.of("/tmp/SKILL.md"),
                        "content", List.of()))
        );
        manager.activateSkill("session-1", "skill-a");
        assertFalse(manager.getActiveSkills("session-1").isEmpty());

        manager.clearSessionSkills("session-1");
        assertEquals(0, manager.getActiveSkills("session-1").size());
    }

    @Test
    void buildSkillPromptBlockReturnsEmptyForNoActiveSkills() {
        assertEquals("", manager.buildSkillPromptBlock("empty-session"));
    }

    @Test
    void buildSkillPromptBlockIncludesActiveSkills() {
        SkillDefinition def = new SkillDefinition("test-skill", "Test",
                List.of(), List.of(), List.of(), Path.of("/tmp"), Path.of("/tmp/SKILL.md"),
                "# Test Content", List.of());
        when(registry.getSkill("test-skill")).thenReturn(java.util.Optional.of(def));
        when(preprocessor.preprocess("# Test Content", Path.of("/tmp"), "s1"))
                .thenReturn("[PREPROCESSED] # Test Content");

        manager.activateSkill("s1", "test-skill");
        String block = manager.buildSkillPromptBlock("s1");

        assertTrue(block.contains("Active Skills"));
        assertTrue(block.contains("test-skill"));
        assertTrue(block.contains("[PREPROCESSED]"));
    }
}
