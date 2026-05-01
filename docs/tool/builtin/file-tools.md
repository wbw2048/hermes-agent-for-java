# FileTools 设计文档

## 概述

文件操作工具集，提供 LLM 读写、替换和搜索文件的能力，避免 LLM 直接使用 shell 命令（cat/grep/sed）。

## 工具类

```java
@Service
@ToolSet("file")
public class FileTools {
    // 读取文本文件，支持分页
    public String readFile(String path, Integer offset, Integer limit)

    // 写入文件，完全覆盖
    public String writeFile(String path, String content)

    // 查找替换文本
    public String patch(String path, String oldString, String newString, Boolean replaceAll)

    // 搜索文件内容或文件名
    public String searchFiles(String pattern, String target, String path, Integer limit)
}
```

## 路径安全验证 (`PathValidator`)

`PathValidator` 提供静态验证方法：

| 方法 | 功能 |
|------|------|
| `validateReadPath()` | 检查设备文件（/dev/*）和二进制扩展名 |
| `validateWritePath()` | 检查敏感系统路径（/etc/, /boot/, docker.sock） |
| `hasPathTraversal()` | 检查 `..` 路径穿越 |
| `resolvePath()` | 解析为标准化绝对路径 |

## 安全限制

- 阻止读取 `/dev/` 设备文件（防止无限输出或阻塞）
- 阻止读取二进制文件（图片、压缩包、可执行文件等）
- 阻止写入 `/etc/`, `/boot/`, `/usr/lib/systemd/` 等敏感路径
- 最大读取字符数限制 100K，超出时提示使用 offset/limit
- 文件不存在时返回相似文件名建议

## 数据格式

所有工具返回 JSON 字符串，便于 LLM 解析：

```json
// readFile 成功
{"path": "...", "total_lines": 100, "content": "...", "truncated": false}

// writeFile 成功
{"success": true, "path": "...", "bytes_written": 123}

// patch 成功
{"success": true, "path": "...", "replacements": 3}

// searchFiles 成功
{"matches": ["file1.txt", "file2.txt"], "count": 2, "target": "files"}

// 错误
{"error": "File not found: ..."}
```

## 依赖关系

- **上游**: Java NIO (`java.nio.file.Files`, `java.nio.file.Path`)
- **下游**: 通过 Spring AI `@Tool` 注解注册到 LLM
- **同级**: `PathValidator`（路径安全检查）

## 测试要点

- 基本读写功能
- 分页读取（offset/limit）
- patch 的单个替换和全部替换
- 文件内容搜索和文件名搜索
- 文件不存在时的相似文件建议
- 安全限制：设备文件、二进制文件、敏感路径
- 创建嵌套父目录
