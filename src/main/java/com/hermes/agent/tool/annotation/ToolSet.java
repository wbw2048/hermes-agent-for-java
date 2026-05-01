package com.hermes.agent.tool.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记工具类所属的工具集。
 * <p>
 * ToolSetManager 根据此注解和 application.yml 中的活跃工具集配置，
 * 决定是否将该 Bean 注册到 SimpleAgent 中。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolSet {

    /**
     * 工具集名称，对应 application.yml 中的 hermes.tools.toolsets.active 列表。
     */
    String value();
}
