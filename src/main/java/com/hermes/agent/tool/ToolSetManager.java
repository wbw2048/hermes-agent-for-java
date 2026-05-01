package com.hermes.agent.tool;

import com.hermes.agent.config.ToolSetProperties;
import com.hermes.agent.tool.annotation.ToolSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理工具集的激活状态，过滤出应注册到智能体的工具 Bean。
 * <p>
 * 根据 @ToolSet 注解和 application.yml 配置，
 * 决定哪些工具 Bean 参与 LLM 的工具调用。
 */
@Component
public class ToolSetManager {

    private static final Logger log = LoggerFactory.getLogger(ToolSetManager.class);

    private final ToolSetProperties properties;
    private final Set<String> activeToolSets;

    public ToolSetManager(ToolSetProperties properties) {
        this.properties = properties;
        this.activeToolSets = Set.copyOf(properties.getActive());
        log.info("ToolSetManager initialized with active toolsets: {}", activeToolSets);
    }

    /**
     * 从所有候选工具 Bean 中过滤出活跃工具集的 Bean。
     *
     * @param allToolBeans 所有 Spring 工具 Bean 实例
     * @return 仅包含活跃工具集的工具 Bean
     */
    public List<Object> getActiveToolBeans(List<Object> allToolBeans) {
        return allToolBeans.stream()
                .filter(this::isInActiveToolSet)
                .collect(Collectors.toList());
    }

    /**
     * 检查 Bean 是否属于活跃的工具集。
     */
    private boolean isInActiveToolSet(Object bean) {
        ToolSet annotation = bean.getClass().getAnnotation(ToolSet.class);
        if (annotation == null) {
            // 无 @ToolSet 注解的 Bean 视为始终启用
            return true;
        }
        return activeToolSets.contains(annotation.value());
    }

    /**
     * 返回当前活跃的工具集名称列表。
     */
    public List<String> getActiveToolSetNames() {
        return new ArrayList<>(activeToolSets);
    }

    /**
     * 返回所有已注册工具集的元数据。
     */
    public Map<String, ToolSetProperties.ToolSetDefinition> getAllDefinitions() {
        return properties.getAll();
    }
}
