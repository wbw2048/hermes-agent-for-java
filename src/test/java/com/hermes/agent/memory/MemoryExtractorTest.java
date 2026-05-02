package com.hermes.agent.memory;

import com.hermes.agent.config.MemoryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryExtractor 单元测试。
 * 由于 MemoryExtractor 依赖 ChatClient（需要实际的 LLM 连接），
 * 这里只测试配置开关逻辑。
 */
class MemoryExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void disabledAutoExtract() {
        MemoryProperties props = new MemoryProperties();
        props.setEnabled(false);
        props.setAutoExtract(false);

        assertFalse(props.isEnabled());
        assertFalse(props.isAutoExtract());
    }

    @Test
    void enabledAutoExtract() {
        MemoryProperties props = new MemoryProperties();
        props.setEnabled(true);
        props.setAutoExtract(true);

        assertTrue(props.isEnabled());
        assertTrue(props.isAutoExtract());
    }
}
