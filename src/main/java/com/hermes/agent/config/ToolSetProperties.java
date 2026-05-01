package com.hermes.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * 绑定 hermes.tools.toolsets.* 配置属性。
 */
@ConfigurationProperties(prefix = "hermes.tools.toolsets")
public class ToolSetProperties {

    /**
     * 当前启用的工具集名称列表。
     */
    private List<String> active = List.of();

    /**
     * 所有工具集的元数据定义（描述、是否启用等）。
     */
    private Map<String, ToolSetDefinition> all = Map.of();

    public List<String> getActive() {
        return active != null ? active : List.of();
    }

    public void setActive(List<String> active) {
        this.active = active;
    }

    public Map<String, ToolSetDefinition> getAll() {
        return all != null ? all : Map.of();
    }

    public void setAll(Map<String, ToolSetDefinition> all) {
        this.all = all;
    }

    /**
     * 检查指定工具集是否处于启用状态。
     */
    public boolean isActive(String toolSetName) {
        return active.contains(toolSetName);
    }

    public record ToolSetDefinition(String description, boolean enabled) {
    }
}
