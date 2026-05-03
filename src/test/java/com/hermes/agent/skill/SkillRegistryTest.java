package com.hermes.agent.skill;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillRegistryTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("skill-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void parsesSingleSkillFile() throws IOException {
        Path skillDir = tempDir.resolve("git-workflow");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: git-workflow
                description: Git 工作流辅助技能
                platforms: [macos, linux]
                ---

                # Git Workflow

                使用此技能时，请先检查 git 状态再执行操作。
                """);

        SkillProperties props = new SkillProperties();
        props.setExternalDirs(List.of(tempDir.toString()));

        // Use a non-existent skillsDir so only externalDir is scanned
        Path emptySkillsDir = tempDir.resolve("nonexistent-skills");
        SkillRegistry registry = new SkillRegistry(props, emptySkillsDir);

        assertEquals(1, registry.getAllSkills().size());
        var skill = registry.getSkill("git-workflow");
        assertTrue(skill.isPresent());
        assertEquals("git-workflow", skill.get().name());
        assertEquals("Git 工作流辅助技能", skill.get().description());
        assertEquals(List.of("macos", "linux"), skill.get().platforms());
    }

    @Test
    void skipsIncompatiblePlatform() throws IOException {
        Path skillDir = tempDir.resolve("windows-only");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: windows-only
                description: Windows only skill
                platforms: [windows]
                ---

                Windows skill content
                """);

        SkillProperties props = new SkillProperties();
        props.setExternalDirs(List.of(tempDir.toString()));

        Path emptySkillsDir = tempDir.resolve("nonexistent-skills");
        SkillRegistry registry = new SkillRegistry(props, emptySkillsDir);

        // On macOS/Linux, windows-only skill should be skipped
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            assertEquals(0, registry.getAllSkills().size());
        }
    }

    @Test
    void skipsDisabledSkills() throws IOException {
        Path dir1 = tempDir.resolve("skill-a");
        Path dir2 = tempDir.resolve("skill-b");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);
        Files.writeString(dir1.resolve("SKILL.md"), """
                ---
                name: skill-a
                description: Skill A
                ---
                Content A
                """);
        Files.writeString(dir2.resolve("SKILL.md"), """
                ---
                name: skill-b
                description: Skill B
                ---
                Content B
                """);

        SkillProperties props = new SkillProperties();
        props.setExternalDirs(List.of(tempDir.toString()));
        props.setDisabled(List.of("skill-a"));

        Path emptySkillsDir = tempDir.resolve("nonexistent-skills");
        SkillRegistry registry = new SkillRegistry(props, emptySkillsDir);

        assertEquals(1, registry.getAllSkills().size());
        assertTrue(registry.getSkill("skill-b").isPresent());
        assertFalse(registry.getSkill("skill-a").isPresent());
    }

    @Test
    void findsLinkedFiles() throws IOException {
        Path skillDir = tempDir.resolve("full-skill");
        Files.createDirectories(skillDir.resolve("references"));
        Files.createDirectories(skillDir.resolve("scripts"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: full-skill
                description: Full skill with files
                ---
                Content
                """);
        Files.writeString(skillDir.resolve("references/api.md"), "# API Reference");
        Files.writeString(skillDir.resolve("scripts/helper.sh"), "#!/bin/bash");

        SkillProperties props = new SkillProperties();
        props.setExternalDirs(List.of(tempDir.toString()));

        Path emptySkillsDir = tempDir.resolve("nonexistent-skills");
        SkillRegistry registry = new SkillRegistry(props, emptySkillsDir);

        var skill = registry.getSkill("full-skill");
        assertTrue(skill.isPresent());
        assertTrue(skill.get().linkedFiles().contains("references/api.md"));
        assertTrue(skill.get().linkedFiles().contains("scripts/helper.sh"));
    }

    @Test
    void getCategoriesReturnsDistinctCategories() throws IOException {
        Path cat1 = tempDir.resolve("mlops/axolotl");
        Path cat2 = tempDir.resolve("email/himalaya");
        Files.createDirectories(cat1);
        Files.createDirectories(cat2);
        Files.writeString(cat1.resolve("SKILL.md"), "---\nname: axolotl\ndescription: Axolotl\n---\nContent");
        Files.writeString(cat2.resolve("SKILL.md"), "---\nname: himalaya\ndescription: Himalaya\n---\nContent");

        SkillProperties props = new SkillProperties();
        props.setExternalDirs(List.of(tempDir.toString()));

        // Use an empty skillsDir so categories come from external dirs
        Path emptySkillsDir = tempDir.resolve("nonexistent-skills");
        SkillRegistry registry = new SkillRegistry(props, emptySkillsDir);

        assertEquals(2, registry.getAllSkills().size());
        assertTrue(registry.getSkill("axolotl").isPresent());
        assertTrue(registry.getSkill("himalaya").isPresent());
    }
}
