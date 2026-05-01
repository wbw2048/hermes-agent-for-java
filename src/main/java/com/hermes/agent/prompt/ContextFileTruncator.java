package com.hermes.agent.prompt;

/**
 * 上下文文件截断器。
 * 对过长的上下文文件执行 head/tail 分割，保留头部 70% 和尾部 20%。
 */
public class ContextFileTruncator {

    /** 上下文文件最大字符数。 */
    public static final int MAX_CHARS = 20_000;
    /** 头部保留比例。 */
    private static final double HEAD_RATIO = 0.7;
    /** 尾部保留比例。 */
    private static final double TAIL_RATIO = 0.2;

    /**
     * 截断内容，若超过最大长度则保留头部和尾部。
     *
     * @param content  原始内容
     * @param filename 文件名（用于截断标记）
     * @param maxChars 最大字符数
     * @return 截断后的内容
     */
    public String truncate(String content, String filename, int maxChars) {
        if (content.length() <= maxChars) {
            return content;
        }
        int headChars = (int) (maxChars * HEAD_RATIO);
        int tailChars = (int) (maxChars * TAIL_RATIO);
        String head = content.substring(0, headChars);
        String tail = content.substring(content.length() - tailChars);
        return head + "\n\n[...truncated " + filename + ": kept " + headChars + "+" + tailChars + " of " + content.length() + " chars. Use file tools to read the full file.]\n\n" + tail;
    }

    /**
     * 使用默认最大字符数截断。
     */
    public String truncate(String content, String filename) {
        return truncate(content, filename, MAX_CHARS);
    }
}
