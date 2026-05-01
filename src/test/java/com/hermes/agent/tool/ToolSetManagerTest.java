package com.hermes.agent.tool;

import com.hermes.agent.config.ToolSetProperties;
import com.hermes.agent.tool.annotation.ToolSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolSetManager} 的测试。
 * 覆盖：工具集过滤、无注解 Bean 处理、活跃工具集名称。
 */
class ToolSetManagerTest {

    @Test
    void filtersByActiveToolSets() {
        ToolSetProperties props = new ToolSetProperties();
        props.setActive(List.of("alpha", "beta"));
        props.setAll(Map.of(
                "alpha", new ToolSetProperties.ToolSetDefinition("Alpha tools", true),
                "beta", new ToolSetProperties.ToolSetDefinition("Beta tools", true),
                "gamma", new ToolSetProperties.ToolSetDefinition("Gamma tools", false)
        ));

        ToolSetManager manager = new ToolSetManager(props);

        @ToolSet("alpha")
        class AlphaBean {
        }
        @ToolSet("gamma")
        class GammaBean {
        }

        List<Object> allBeans = List.of(new AlphaBean(), new GammaBean());
        List<Object> active = manager.getActiveToolBeans(allBeans);

        assertEquals(1, active.size());
        assertTrue(active.get(0) instanceof AlphaBean);
    }

    @Test
    void beansWithoutToolSetAnnotationAlwaysActive() {
        ToolSetProperties props = new ToolSetProperties();
        props.setActive(List.of("alpha"));

        ToolSetManager manager = new ToolSetManager(props);

        class NoAnnotationBean {
        }
        @ToolSet("other")
        class OtherBean {
        }

        List<Object> allBeans = List.of(new NoAnnotationBean(), new OtherBean());
        List<Object> active = manager.getActiveToolBeans(allBeans);

        assertEquals(1, active.size());
        assertTrue(active.get(0) instanceof NoAnnotationBean);
    }

    @Test
    void getActiveToolSetNames() {
        ToolSetProperties props = new ToolSetProperties();
        props.setActive(List.of("file", "terminal"));

        ToolSetManager manager = new ToolSetManager(props);
        List<String> names = manager.getActiveToolSetNames();

        assertEquals(2, names.size());
        assertTrue(names.contains("file"));
        assertTrue(names.contains("terminal"));
    }

    @Test
    void getAllDefinitions() {
        ToolSetProperties props = new ToolSetProperties();
        props.setActive(List.of("datetime"));
        props.setAll(Map.of(
                "datetime", new ToolSetProperties.ToolSetDefinition("日期时间工具", true),
                "echo", new ToolSetProperties.ToolSetDefinition("回声测试工具", true)
        ));

        ToolSetManager manager = new ToolSetManager(props);
        Map<String, ToolSetProperties.ToolSetDefinition> defs = manager.getAllDefinitions();

        assertEquals(2, defs.size());
        assertEquals("日期时间工具", defs.get("datetime").description());
    }
}
