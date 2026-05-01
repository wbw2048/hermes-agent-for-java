package com.hermes.agent.tool.builtin;

import java.nio.file.Path;
import java.util.Set;

/**
 * 文件路径安全验证器。
 * <p>
 * 防止路径穿越（..）、设备文件读取（/dev/）和敏感系统路径写入（/etc/, /boot/）。
 */
public final class PathValidator {

    private static final Set<String> BLOCKED_DEVICE_PREFIXES = Set.of(
            "/dev/zero", "/dev/random", "/dev/urandom", "/dev/full",
            "/dev/stdin", "/dev/tty", "/dev/console",
            "/dev/stdout", "/dev/stderr"
    );

    private static final Set<String> SENSITIVE_PATH_PREFIXES = Set.of(
            "/etc/", "/boot/", "/usr/lib/systemd/",
            "/private/etc/", "/private/var/"
    );

    private static final Set<String> SENSITIVE_EXACT_PATHS = Set.of(
            "/var/run/docker.sock", "/run/docker.sock"
    );

    private PathValidator() {
    }

    /**
     * 验证路径是否在允许范围内。
     *
     * @param filePath 用户指定的文件路径
     * @return 如果不安全，返回错误信息；如果安全，返回 null
     */
    public static String validateReadPath(String filePath) {
        if (isBlockedDevice(filePath)) {
            return "Cannot read '" + filePath + "': this is a device file that would block or produce infinite output.";
        }
        if (isBinaryExtension(filePath)) {
            return "Cannot read binary file '" + filePath + "'. Use vision_analyze for images.";
        }
        return null;
    }

    /**
     * 验证写入路径是否安全。
     *
     * @param filePath 用户指定的写入路径
     * @return 如果不安全，返回错误信息；如果安全，返回 null
     */
    public static String validateWritePath(String filePath) {
        try {
            Path resolved = resolvePath(filePath);
            String resolvedStr = resolved.toString();
            for (String prefix : SENSITIVE_PATH_PREFIXES) {
                if (resolvedStr.startsWith(prefix)) {
                    return "Refusing to write to sensitive system path: " + filePath
                            + "\nUse the terminal tool with sudo if you need to modify system files.";
                }
            }
            if (SENSITIVE_EXACT_PATHS.contains(resolvedStr)) {
                return "Refusing to write to sensitive system path: " + filePath
                        + "\nUse the terminal tool with sudo if you need to modify system files.";
            }
        } catch (Exception e) {
            return "Invalid path: " + filePath;
        }
        return null;
    }

    /**
     * 检查是否包含路径穿越组件（..）。
     */
    public static boolean hasPathTraversal(String filePath) {
        return filePath.contains("..");
    }

    /**
     * 解析路径为绝对路径。
     */
    public static Path resolvePath(String filePath) {
        return Path.of(filePath).toAbsolutePath().normalize();
    }

    private static boolean isBlockedDevice(String filePath) {
        String expanded = expandUser(filePath);
        for (String prefix : BLOCKED_DEVICE_PREFIXES) {
            if (expanded.startsWith(prefix)) {
                return true;
            }
        }
        if (expanded.startsWith("/proc/") && (expanded.endsWith("/fd/0") || expanded.endsWith("/fd/1") || expanded.endsWith("/fd/2"))) {
            return true;
        }
        return false;
    }

    private static boolean isBinaryExtension(String filePath) {
        String ext = Path.of(filePath).toString().toLowerCase();
        return ext.endsWith(".png") || ext.endsWith(".jpg") || ext.endsWith(".jpeg")
                || ext.endsWith(".gif") || ext.endsWith(".bmp") || ext.endsWith(".webp")
                || ext.endsWith(".ico") || ext.endsWith(".tiff") || ext.endsWith(".svg")
                || ext.endsWith(".pdf") || ext.endsWith(".zip") || ext.endsWith(".tar")
                || ext.endsWith(".gz") || ext.endsWith(".jar") || ext.endsWith(".war")
                || ext.endsWith(".class") || ext.endsWith(".so") || ext.endsWith(".dll")
                || ext.endsWith(".exe") || ext.endsWith(".dylib") || ext.endsWith(".o")
                || ext.endsWith(".a") || ext.endsWith(".lib");
    }

    private static String expandUser(String filePath) {
        if (filePath.startsWith("~")) {
            return System.getProperty("user.home") + filePath.substring(1);
        }
        return filePath;
    }
}
