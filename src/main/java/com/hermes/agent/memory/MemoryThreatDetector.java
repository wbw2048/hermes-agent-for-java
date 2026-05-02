package com.hermes.agent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 记忆内容威胁检测器。
 * 扫描记忆条目中的注入/泄密模式，防止恶意内容进入系统提示。
 */
@Component
public class MemoryThreatDetector {

    private static final Logger log = LoggerFactory.getLogger(MemoryThreatDetector.class);

    private static final Pattern[] THREAT_PATTERNS = {
        Pattern.compile("ignore\\s+(previous|all|above|prior)\\s+instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("you\\s+are\\s+now\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("do\\s+not\\s+tell\\s+the\\s+user", Pattern.CASE_INSENSITIVE),
        Pattern.compile("system\\s+prompt\\s+override", Pattern.CASE_INSENSITIVE),
        Pattern.compile("disregard\\s+(your|all|any)\\s+(instructions|rules|guidelines)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("act\\s+as\\s+(if|though)\\s+you\\s+(have\\s+no|don't\\s+have)\\s+(restrictions|limits|rules)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("curl\\s+[^\\n]*\\$?\\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("wget\\s+[^\\n]*\\$?\\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("cat\\s+[^\\n]*(\\.env|credentials|\\.netrc|\\.pgpass|\\.npmrc|\\.pypirc)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("authorized_keys", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\$HOME/\\.ssh|~/\\.ssh", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\$HOME/\\.hermes/\\.env|~/\\.hermes/\\.env", Pattern.CASE_INSENSITIVE),
    };

    private static final String[] THREAT_IDS = {
        "prompt_injection", "role_hijack", "deception_hide", "sys_prompt_override",
        "disregard_rules", "bypass_restrictions",
        "exfil_curl", "exfil_wget", "read_secrets",
        "ssh_backdoor", "ssh_access", "hermes_env",
    };

    private static final char[] INVISIBLE_CHARS = {
        '​', '‌', '‍', '⁠', '﻿',
        '‪', '‫', '‬', '‭', '‮',
    };

    /**
     * 扫描内容中的威胁模式。
     *
     * @param content 待扫描内容
     * @return 若检测到威胁返回错误信息，否则返回 null
     */
    public String scan(String content) {
        // 检查不可见 Unicode 字符
        for (char c : INVISIBLE_CHARS) {
            if (content.indexOf(c) >= 0) {
                return String.format(
                    "Blocked: content contains invisible unicode character U+%04X (possible injection).", (int) c);
            }
        }

        // 检查威胁模式
        for (int i = 0; i < THREAT_PATTERNS.length; i++) {
            if (THREAT_PATTERNS[i].matcher(content).find()) {
                return String.format(
                    "Blocked: content matches threat pattern '%s'. "
                    + "Memory entries are injected into the system prompt and must not contain injection or exfiltration payloads.",
                    THREAT_IDS[i]);
            }
        }

        return null;
    }
}
