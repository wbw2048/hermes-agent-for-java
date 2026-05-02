package com.hermes.agent.memory;

import com.hermes.agent.config.MemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 记忆系统编排器。
 * <p>
 * 管理内置 provider 和最多一个外部 provider。所有 provider 失败互不影响。
 * 提供统一的系统提示构建、prefetch、同步和工具路由接口。
 */
@Component
public class MemoryManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryManager.class);

    private final List<MemoryProvider> providers = new ArrayList<>();
    private boolean hasExternal = false;

    private final MemoryProperties properties;

    public MemoryManager(MemoryProperties properties) {
        this.properties = properties;
    }

    /**
     * 注册记忆提供者。
     *
     * @param provider 提供者实例
     */
    public void addProvider(MemoryProvider provider) {
        boolean isBuiltin = "builtin".equals(provider.name());

        if (!isBuiltin) {
            if (hasExternal) {
                String existing = providers.stream()
                    .filter(p -> !"builtin".equals(p.name()))
                    .map(MemoryProvider::name)
                    .findFirst()
                    .orElse("unknown");
                log.warn("Rejected memory provider '{}' — external provider '{}' is already registered. "
                    + "Only one external memory provider is allowed at a time.", provider.name(), existing);
                return;
            }
            hasExternal = true;
        }

        providers.add(provider);
        log.info("Memory provider '{}' registered", provider.name());
    }

    /**
     * 所有注册的提供者列表。
     */
    public List<MemoryProvider> getProviders() {
        return List.copyOf(providers);
    }

    /**
     * 从所有提供者收集系统提示块。
     *
     * @return 合并的系统提示文本，空字符串表示无内容
     */
    public String buildSystemPrompt() {
        List<String> blocks = new ArrayList<>();
        for (MemoryProvider provider : providers) {
            try {
                String block = provider.systemPromptBlock();
                if (block != null && !block.isBlank()) {
                    blocks.add(block);
                }
            } catch (Exception e) {
                log.warn("Memory provider '{}' systemPromptBlock() failed: {}", provider.name(), e.getMessage());
            }
        }
        return String.join("\n\n", blocks);
    }

    /**
     * 从所有提供者预取记忆上下文。
     *
     * @param query 用户消息
     * @return 合并的记忆上下文文本
     */
    public String prefetchAll(String query) {
        if (!properties.isEnabled()) return "";

        List<String> parts = new ArrayList<>();
        for (MemoryProvider provider : providers) {
            try {
                String result = provider.prefetch(query);
                if (result != null && !result.isBlank()) {
                    parts.add(result);
                }
            } catch (Exception e) {
                log.debug("Memory provider '{}' prefetch failed (non-fatal): {}", provider.name(), e.getMessage());
            }
        }
        return String.join("\n\n", parts);
    }

    /**
     * 在所有提供者上排队后台 prefetch。
     */
    public void queuePrefetchAll(String query) {
        if (!properties.isEnabled()) return;

        for (MemoryProvider provider : providers) {
            try {
                provider.queuePrefetch(query);
            } catch (Exception e) {
                log.debug("Memory provider '{}' queuePrefetch failed: {}", provider.name(), e.getMessage());
            }
        }
    }

    /**
     * 同步完成的对话轮次到所有提供者。
     */
    public void syncAll(String userContent, String assistantContent) {
        if (!properties.isEnabled()) return;

        for (MemoryProvider provider : providers) {
            try {
                provider.syncTurn(userContent, assistantContent);
            } catch (Exception e) {
                log.warn("Memory provider '{}' syncTurn failed: {}", provider.name(), e.getMessage());
            }
        }
    }

    /**
     * 从所有提供者收集工具 schema。
     */
    public List<Map<String, Object>> getAllToolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (MemoryProvider provider : providers) {
            try {
                schemas.addAll(provider.getToolSchemas());
            } catch (Exception e) {
                log.warn("Memory provider '{}' getToolSchemas() failed: {}", provider.name(), e.getMessage());
            }
        }
        return schemas;
    }

    /**
     * 初始化所有提供者。
     */
    public void initializeAll(String sessionId) {
        if (!properties.isEnabled()) return;

        Map<String, Object> kwargs = Map.of("hermesHome", properties.getHomeDir(), "platform", "web");
        for (MemoryProvider provider : providers) {
            try {
                provider.initialize(sessionId, kwargs);
            } catch (Exception e) {
                log.warn("Memory provider '{}' initialize failed: {}", provider.name(), e.getMessage());
            }
        }
    }

    /**
     * 关闭所有提供者。
     */
    public void shutdownAll() {
        for (int i = providers.size() - 1; i >= 0; i--) {
            try {
                providers.get(i).shutdown();
            } catch (Exception e) {
                log.warn("Memory provider '{}' shutdown failed: {}", providers.get(i).name(), e.getMessage());
            }
        }
    }

    /**
     * 通知所有提供者会话切换。
     */
    public void onSessionSwitch(String newSessionId, String parentSessionId, boolean reset) {
        if (!properties.isEnabled()) return;

        for (MemoryProvider provider : providers) {
            try {
                provider.onSessionSwitch(newSessionId, parentSessionId, reset);
            } catch (Exception e) {
                log.debug("Memory provider '{}' onSessionSwitch failed: {}", provider.name(), e.getMessage());
            }
        }
    }

    /**
     * 通知所有提供者会话结束。
     */
    public void onSessionEnd(List<Map<String, String>> messages) {
        if (!properties.isEnabled()) return;

        for (MemoryProvider provider : providers) {
            try {
                provider.onSessionEnd(messages);
            } catch (Exception e) {
                log.debug("Memory provider '{}' onSessionEnd failed: {}", provider.name(), e.getMessage());
            }
        }
    }

    /**
     * 通知所有提供者压缩前事件。
     */
    public String onPreCompress(List<Map<String, String>> messages) {
        if (!properties.isEnabled()) return "";

        List<String> parts = new ArrayList<>();
        for (MemoryProvider provider : providers) {
            try {
                String result = provider.onPreCompress(messages);
                if (result != null && !result.isBlank()) {
                    parts.add(result);
                }
            } catch (Exception e) {
                log.debug("Memory provider '{}' onPreCompress failed: {}", provider.name(), e.getMessage());
            }
        }
        return String.join("\n\n", parts);
    }
}
