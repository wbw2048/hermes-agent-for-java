package com.hermes.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能内容预处理器。
 * 处理模板变量替换（${HERMES_SKILL_DIR}、${HERMES_SESSION_ID}）
 * 和 inline shell 展开（!`command`）。
 */
@Component
public class SkillPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(SkillPreprocessor.class);

    private static final Pattern TEMPLATE_VAR = Pattern.compile("\\$\\{(HERMES_SKILL_DIR|HERMES_SESSION_ID)}");
    private static final Pattern INLINE_SHELL = Pattern.compile("!`([^`\n]+)`");
    private static final int INLINE_SHELL_MAX_OUTPUT = 4000;

    private final SkillProperties properties;

    public SkillPreprocessor(SkillProperties properties) {
        this.properties = properties;
    }

    /**
     * 对技能内容应用预处理的入口。
     *
     * @param content   技能原始内容（含 frontmatter）
     * @param skillDir  技能所在目录
     * @param sessionId 会话 ID
     * @return 预处理后的内容
     */
    public String preprocess(String content, Path skillDir, String sessionId) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        if (properties.isTemplateVars()) {
            content = substituteTemplateVars(content, skillDir, sessionId);
        }
        if (properties.isInlineShell()) {
            content = expandInlineShell(content, skillDir);
        }
        return content;
    }

    /**
     * 替换模板变量。
     * 仅当对应值存在时才替换，未解析的标记原样保留。
     */
    String substituteTemplateVars(String content, Path skillDir, String sessionId) {
        String skillDirStr = skillDir != null ? skillDir.toString() : null;
        return TEMPLATE_VAR.matcher(content).replaceAll(match -> {
            String token = match.group(1);
            if ("HERMES_SKILL_DIR".equals(token) && skillDirStr != null) {
                return Matcher.quoteReplacement(skillDirStr);
            }
            if ("HERMES_SESSION_ID".equals(token) && sessionId != null) {
                return Matcher.quoteReplacement(sessionId);
            }
            // 保留未解析的标记 — 需要转义 $ 以防止 regex 引擎误解析
            return Matcher.quoteReplacement(match.group(0));
        });
    }

    /**
     * 展开 inline shell 片段。
     * 将 !`command` 替换为命令执行结果。
     */
    String expandInlineShell(String content, Path skillDir) {
        if (!content.contains("!`")) {
            return content;
        }
        return INLINE_SHELL.matcher(content).replaceAll(match -> {
            String cmd = match.group(1).trim();
            if (cmd.isEmpty()) return "";
            return executeShell(cmd, skillDir);
        });
    }

    private String executeShell(String command, Path cwd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            if (cwd != null) {
                pb.directory(cwd.toFile());
            }
            Process proc = pb.start();
            boolean finished = proc.waitFor(properties.getInlineShellTimeout(), TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return "[inline-shell timeout after " + properties.getInlineShellTimeout() + "s: " + command + "]";
            }

            String output = new String(proc.getInputStream().readAllBytes()).stripTrailing();
            if (output.isEmpty()) {
                output = new String(proc.getErrorStream().readAllBytes()).stripTrailing();
            }
            if (output.length() > INLINE_SHELL_MAX_OUTPUT) {
                output = output.substring(0, INLINE_SHELL_MAX_OUTPUT) + "...[truncated]";
            }
            return output;
        } catch (IOException e) {
            return "[inline-shell error: " + e.getMessage() + "]";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[inline-shell interrupted]";
        }
    }
}
