package com.hermes.agent.config;

import com.hermes.agent.memory.MemoryManager;
import com.hermes.agent.memory.MemoryProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 记忆系统自动配置。
 * <p>
 * 启动时将所有 Spring 管理的 {@link MemoryProvider} Bean 注册到 {@link MemoryManager}。
 */
@Configuration
public class MemoryConfig {

    /**
     * 自动注册所有 MemoryProvider Bean 到 MemoryManager。
     *
     * @param memoryManager 记忆管理器
     * @param providers     所有 MemoryProvider Bean（如 BuiltinMemoryProvider）
     * @return 注册完成后的 MemoryManager
     */
    @Bean
    public MemoryManager memoryManagerRegistrar(MemoryManager memoryManager, List<MemoryProvider> providers) {
        for (MemoryProvider provider : providers) {
            memoryManager.addProvider(provider);
        }
        return memoryManager;
    }
}
