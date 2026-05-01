package com.hermes.agent.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 上下文文件注入检测器。
 * 扫描上下文文件内容中的提示注入模式，检测到威胁时返回标记为 BLOCKED 的内容。
 */
public class PromptInjectionDetector {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionDetector.class);

    /** 注入威胁正则模式（与 Python 参考实现一致）。 */
    private static final List<ThreatPattern> THREAT_PATTERNS = List.of(
        new ThreatPattern("ignore\\s+(previous|all|above|prior)\\s+instructions", "prompt_injection"),
        new ThreatPattern("do\\s+not\\s+tell\\s+the\\s+user", "deception_hide"),
        new ThreatPattern("system\\s+prompt\\s+override", "sys_prompt_override"),
        new ThreatPattern("disregard\\s+(your|all|any)\\s+(instructions|rules|guidelines)", "disregard_rules"),
        new ThreatPattern("act\\s+as\\s+(if|though)\\s+you\\s+(have\\s+no|don't\\s+have)\\s+(restrictions|limits|rules)", "bypass_restrictions"),
        new ThreatPattern("<!--[^>]*(?:ignore|override|system|secret|hidden)[^>]*-->", "html_comment_injection"),
        new ThreatPattern("(?s)<\\s*div\\s+style\\s*=[\"'][^\"']*display\\s*:\\s*none", "hidden_div"),
        new ThreatPattern("translate\\s+.*\\s+into\\s+.*\\s+and\\s+(execute|run|eval)", "translate_execute"),
        new ThreatPattern("curl\\s+[^\\n]*\\$\\{?\\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)", "exfil_curl"),
        new ThreatPattern("cat\\s+[^\\n]*(\\.env|credentials|\\.netrc|\\.pgpass)", "read_secrets")
    );

    /** 不可见 Unicode 字符代码点集合。 */
    private static final int[] INVISIBLE_CODEPOINTS = {
        0x200B, // zero-width space
        0x200C, // zero-width non-joiner
        0x200D, // zero-width joiner
        0x2060, // word joiner
        0xFEFF, // zero-width no-break space / BOM
        0x202A, // left-to-right embedding
        0x202B, // right-to-left embedding
        0x202C, // pop directional formatting
        0x202D, // left-to-right override
        0x202E, // right-to-left override
    };

    /**
     * 扫描上下文文件内容并返回清洗后的内容。
     * 若检测到注入威胁，返回 [BLOCKED: ...] 标记。
     */
    public String scanAndSanitize(String content, String filename) {
        List<String> findings = new java.util.ArrayList<>();

        for (int cp : INVISIBLE_CODEPOINTS) {
            if (content.indexOf(cp) >= 0) {
                findings.add("invisible unicode U+%04X".formatted(cp));
            }
        }

        for (ThreatPattern tp : THREAT_PATTERNS) {
            if (tp.pattern.matcher(content).find()) {
                findings.add(tp.id);
            }
        }

        if (!findings.isEmpty()) {
            log.warn("Context file {} blocked: {}", filename, String.join(", ", findings));
            return "[BLOCKED: %s contained potential prompt injection (%s). Content not loaded.]".formatted(
                filename, String.join(", ", findings));
        }

        return content;
    }

    private record ThreatPattern(Pattern pattern, String id) {
        ThreatPattern(String regex, String id) {
            this(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), id);
        }
    }
}
