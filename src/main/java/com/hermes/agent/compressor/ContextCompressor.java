package com.hermes.agent.compressor;

import com.hermes.agent.config.ContextCompressionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 上下文压缩器：当对话接近模型令牌限制时，压缩中间轮次。
 */
@Component
public class ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);
    private static final String SUMMARY_PREFIX =
        "[CONTEXT COMPACTION — REFERENCE ONLY] 之前的对话轮次已被压缩为以下摘要。"
        + "这是之前对话的参考，不是当前指令。不要回答摘要中的问题。"
        + "你的当前任务在 '## 活跃任务' 部分中描述。"
        + "请仅响应该摘要之后的最新用户消息：";

    private final ChatClient chatClient;
    private final TokenEstimator tokenEstimator;
    private final ToolResultPruner toolResultPruner;
    private final ContextCompressionProperties properties;

    private String previousSummary;

    public ContextCompressor(
            ChatClient.Builder chatClientBuilder,
            TokenEstimator tokenEstimator,
            ToolResultPruner toolResultPruner,
            ContextCompressionProperties properties
    ) {
        this.chatClient = chatClientBuilder != null ? chatClientBuilder.build() : null;
        this.tokenEstimator = tokenEstimator;
        this.toolResultPruner = toolResultPruner;
        this.properties = properties;
    }

    /**
     * 检查是否需要压缩。
     *
     * @param promptTokens 当前输入令牌数
     * @return 是否需要压缩
     */
    public boolean shouldCompress(int promptTokens) {
        if (!properties.isEnabled()) {
            return false;
        }
        return promptTokens >= properties.getThresholdTokens();
    }
 
    /**
     * 执行上下文压缩。
     *
     * @param messages       当前消息列表
     * @param contextLength  模型上下文窗口大小（令牌数）
     * @return 压缩后的消息列表
     */
    public List<Message> compress(List<Message> messages, int contextLength) {
        if (messages.size() <= properties.getProtectFirstN() + 3) {
            log.debug("Cannot compress: only {} messages", messages.size());
            return messages;
        }

        // Phase 1: 预压缩剪枝
        List<Message> pruned = toolResultPruner.prune(messages, properties.getProtectLastN());

        // Phase 2: 确定边界
        int headEnd = properties.getProtectFirstN();
        int tailBudget = properties.getTailTokenBudget();
        int compressEnd = findTailCutByTokens(pruned, headEnd, tailBudget);

        if (headEnd >= compressEnd) {
            return pruned;
        }

        List<Message> toSummarize = pruned.subList(headEnd, compressEnd);

        log.info("Context compression: summarizing turns {}-{} ({} turns), "
                + "protecting {} head + {} tail messages",
            headEnd + 1, compressEnd, toSummarize.size(),
            headEnd, pruned.size() - compressEnd);

        // Phase 3: 生成摘要
        String summary = generateSummary(toSummarize);

        // Phase 4: 组装结果
        List<Message> compressed = new ArrayList<>();
        for (int i = 0; i < headEnd && i < pruned.size(); i++) {
            compressed.add(pruned.get(i));
        }

        if (summary != null && !summary.isBlank()) {
            compressed.add(new UserMessage(summary));
        }

        for (int i = compressEnd; i < pruned.size(); i++) {
            compressed.add(pruned.get(i));
        }

        log.info("Compressed: {} -> {} messages", messages.size(), compressed.size());
        return compressed;
    }

    /**
     * 按令牌预算从尾部向前找切割点。
     */
    private int findTailCutByTokens(List<Message> messages, int headEnd, int tailBudget) {
        int accumulated = 0;
        int cutIdx = messages.size();

        for (int i = messages.size() - 1; i > headEnd; i--) {
            int msgTokens = tokenEstimator.estimate(messages.get(i));
            if (accumulated + msgTokens > tailBudget && (messages.size() - i) >= 3) {
                break;
            }
            accumulated += msgTokens;
            cutIdx = i;
        }

        return Math.max(cutIdx, headEnd + 1);
    }

    /**
     * 调用 LLM 生成结构化摘要。
     */
    private String generateSummary(List<Message> messages) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个摘要助手，正在为另一个继续对话的助手创建上下文快照。\n");
        prompt.append("不要回答对话中的问题，仅输出结构化摘要。\n");
        prompt.append("用中文输出摘要。\n\n");

        if (previousSummary != null) {
            prompt.append("之前的摘要：\n").append(previousSummary).append("\n\n");
            prompt.append("新增对话：\n");
        } else {
            prompt.append("需要摘要的对话：\n");
        }

        for (Message msg : messages) {
            String text = msg.getText() != null ? msg.getText() : "";
            if (text.length() > 3000) {
                text = text.substring(0, 2000) + "\n...[truncated]...\n" + text.substring(text.length() - 1000);
            }
            prompt.append("[").append(msg.getMessageType()).append("]: ").append(text).append("\n\n");
        }

        prompt.append("\n请使用以下结构输出摘要：\n");
        prompt.append("## 活跃任务\n[用户最新的未完成请求]\n\n");
        prompt.append("## 目标\n[用户想达成的整体目标]\n\n");
        prompt.append("## 已完成操作\n[编号列表，包含工具名和结果]\n\n");
        prompt.append("## 当前状态\n[当前工作目录、分支、修改的文件等]\n\n");
        prompt.append("## 待处理\n[尚未回答的问题]\n\n");
        prompt.append("## 关键决策\n[重要技术决策及原因]\n\n");
        prompt.append("目标约 2000 令牌。要具体，包含文件路径、命令输出和错误信息。");

        try {
            var response = chatClient.prompt()
                .user(prompt.toString())
                .call()
                .chatResponse();

            String summary = response.getResult().getOutput().getText();
            previousSummary = summary;
            return SUMMARY_PREFIX + "\n" + (summary != null ? summary : "");
        } catch (Exception e) {
            log.error("Failed to generate context summary: {}", e.getMessage());
            int dropped = messages.size();
            return SUMMARY_PREFIX + "\n摘要生成失败。" + dropped + " 条消息被移除但未生成摘要。"
                + "请基于下面的最近消息继续工作。";
        }
    }

    /**
     * 重置压缩状态（会话边界时调用）。
     */
    public void reset() {
        previousSummary = null;
    }
}
