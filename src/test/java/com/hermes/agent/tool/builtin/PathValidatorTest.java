package com.hermes.agent.tool.builtin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PathValidator} 的测试。
 * 覆盖：路径穿越、设备文件、敏感路径、二进制扩展名检查。
 */
class PathValidatorTest {

    @Test
    void validateReadPathNormalFile() {
        assertNull(PathValidator.validateReadPath("/tmp/test.txt"));
    }

    @Test
    void validateReadPathBlockedDevice() {
        String error = PathValidator.validateReadPath("/dev/zero");
        assertNotNull(error);
        assertTrue(error.contains("device file"));
    }

    @Test
    void validateReadPathBlockedRandom() {
        String error = PathValidator.validateReadPath("/dev/random");
        assertNotNull(error);
    }

    @Test
    void validateReadPathBinaryExtension() {
        String error = PathValidator.validateReadPath("/tmp/image.png");
        assertNotNull(error);
        assertTrue(error.contains("binary"));
    }

    @Test
    void validateReadPathJarFile() {
        String error = PathValidator.validateReadPath("/app/lib.jar");
        assertNotNull(error);
        assertTrue(error.contains("binary"));
    }

    @Test
    void validateWritePathNormalFile() {
        assertNull(PathValidator.validateWritePath("/tmp/test.txt"));
    }

    @Test
    void validateWritePathBlockedSensitive() {
        String error = PathValidator.validateWritePath("/etc/passwd");
        assertNotNull(error);
        assertTrue(error.contains("sensitive system path"));
    }

    @Test
    void validateWritePathBlockedBoot() {
        String error = PathValidator.validateWritePath("/boot/vmlinuz");
        assertNotNull(error);
    }

    @Test
    void validateWritePathBlockedDockerSocket() {
        String error = PathValidator.validateWritePath("/var/run/docker.sock");
        assertNotNull(error);
        assertTrue(error.contains("sensitive system path"));
    }

    @Test
    void hasPathTraversal() {
        assertTrue(PathValidator.hasPathTraversal("../etc/passwd"));
        assertTrue(PathValidator.hasPathTraversal("/tmp/../etc/passwd"));
        assertFalse(PathValidator.hasPathTraversal("/tmp/safe.txt"));
    }

    @Test
    void resolvePath() {
        var resolved = PathValidator.resolvePath("/tmp/test.txt");
        assertTrue(resolved.isAbsolute());
    }
}
