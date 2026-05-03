package com.hermes.agent.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SkillsControllerTest {

    private MockMvc mockMvc;
    private SkillRegistry registry;
    private SkillManager manager;

    @BeforeEach
    void setUp() {
        registry = mock(SkillRegistry.class);
        manager = mock(SkillManager.class);
        SkillsController controller = new SkillsController(registry, manager);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listSkillsReturnsAll() throws Exception {
        SkillDefinition s = new SkillDefinition("git-workflow", "Git workflow",
                List.of(), List.of(), List.of(), Path.of("/tmp"), Path.of("/tmp/SKILL.md"),
                "content", List.of());
        when(registry.getAllSkills()).thenReturn(List.of(s));
        when(registry.getCategories()).thenReturn(List.of("other"));

        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.skills[0].name").value("git-workflow"));
    }

    @Test
    void getSkillReturnsMetadata() throws Exception {
        SkillDefinition s = new SkillDefinition("git-workflow", "Git workflow",
                List.of("macos"), List.of("git"), List.of(), Path.of("/tmp"), Path.of("/tmp/SKILL.md"),
                "content", List.of("refs/api.md"));
        when(registry.getSkill("git-workflow")).thenReturn(Optional.of(s));

        mockMvc.perform(get("/api/skills/git-workflow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("git-workflow"))
                .andExpect(jsonPath("$.description").value("Git workflow"))
                .andExpect(jsonPath("$.linked_files[0]").value("refs/api.md"));
    }

    @Test
    void getSkillNotFound() throws Exception {
        when(registry.getSkill("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/skills/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void activateSkill() throws Exception {
        when(manager.activateSkill("s1", "git-workflow")).thenReturn(true);

        mockMvc.perform(post("/api/skills/git-workflow/activate")
                        .param("sessionId", "s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deactivateSkill() throws Exception {
        when(manager.deactivateSkill("s1", "git-workflow")).thenReturn(true);

        mockMvc.perform(post("/api/skills/git-workflow/deactivate")
                        .param("sessionId", "s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getActiveSkills() throws Exception {
        when(manager.getActiveSkills("s1")).thenReturn(java.util.Set.of("git-workflow"));
        when(manager.getGlobalActiveSkills()).thenReturn(java.util.Set.of());

        mockMvc.perform(get("/api/skills/active")
                        .param("sessionId", "s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_skills[0]").value("git-workflow"));
    }
}
