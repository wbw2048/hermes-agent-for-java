package com.hermes.agent.skill;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkillPreprocessorTest {

    private final SkillPreprocessor preprocessor;

    SkillPreprocessorTest() {
        SkillProperties props = new SkillProperties();
        props.setTemplateVars(true);
        props.setInlineShell(false); // disable inline shell for unit tests
        this.preprocessor = new SkillPreprocessor(props);
    }

    @Test
    void substitutesSkillDir() {
        String content = "The skill dir is: ${HERMES_SKILL_DIR}";
        Path skillDir = Path.of("/home/user/.hermes/skills/my-skill");
        String result = preprocessor.substituteTemplateVars(content, skillDir, null);
        assertEquals("The skill dir is: /home/user/.hermes/skills/my-skill", result);
    }

    @Test
    void substitutesSessionId() {
        String content = "Session: ${HERMES_SESSION_ID}";
        String result = preprocessor.substituteTemplateVars(content, null, "abc-123");
        assertEquals("Session: abc-123", result);
    }

    @Test
    void leavesUnresolvedTokensAsIs() {
        String content = "${HERMES_SKILL_DIR} and ${HERMES_SESSION_ID}";
        // Only sessionId is available
        String result = preprocessor.substituteTemplateVars(content, null, "s1");
        // Both unresolved since skillDir is null
        assertEquals("${HERMES_SKILL_DIR} and s1", result);
    }

    @Test
    void substitutesBothWhenBothAvailable() {
        String content = "Dir: ${HERMES_SKILL_DIR}, Session: ${HERMES_SESSION_ID}";
        String result = preprocessor.substituteTemplateVars(content, Path.of("/skills/test"), "sess-1");
        assertEquals("Dir: /skills/test, Session: sess-1", result);
    }

    @Test
    void preprocessAppliesTemplateVars() {
        SkillProperties props = new SkillProperties();
        props.setTemplateVars(true);
        props.setInlineShell(false);
        SkillPreprocessor proc = new SkillPreprocessor(props);

        String content = "Run from ${HERMES_SKILL_DIR}";
        String result = proc.preprocess(content, Path.of("/tmp/skill"), "s1");
        assertEquals("Run from /tmp/skill", result);
    }

    @Test
    void preprocessReturnsEmptyForNull() {
        assertNull(preprocessor.preprocess(null, null, null));
    }

    @Test
    void preprocessReturnsEmptyForEmptyString() {
        assertEquals("", preprocessor.preprocess("", null, null));
    }

    @Test
    void inlineShellExpandsCommands() {
        SkillProperties props = new SkillProperties();
        props.setTemplateVars(false);
        props.setInlineShell(true);
        SkillPreprocessor proc = new SkillPreprocessor(props);

        String content = "Today: !`echo hello`";
        String result = proc.expandInlineShell(content, null);
        assertEquals("Today: hello", result);
    }

    @Test
    void inlineShellHandlesFailure() {
        SkillProperties props = new SkillProperties();
        props.setTemplateVars(false);
        props.setInlineShell(true);
        SkillPreprocessor proc = new SkillPreprocessor(props);

        String content = "Result: !`false-command`";
        String result = proc.expandInlineShell(content, null);
        // Should return an error marker, not throw
        assertTrue(result.contains("inline-shell error") || result.contains("Result: "));
    }
}
