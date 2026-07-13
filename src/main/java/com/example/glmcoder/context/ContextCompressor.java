package com.example.glmcoder.context;

import com.example.glmcoder.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);

    public static final int MAX_TOKENS = 8000;
    static final int L1_TOOL_RESULT_MAX_LINES = 500;
    static final int L1_TOOL_RESULT_MAX_CHARS = 2000;
    static final int L2_CONVERSATION_ROUND_THRESHOLD = 20;
    static final int L2_SUMMARIZE_OLDEST_ROUNDS = 10;

    private final ConversationService conversationService;

    public ContextCompressor(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public static class CompressedContext {
        public String summary;
        public List<String> keyFiles;
        public List<String> keySymbols;
        public int estimatedTokens;
    }

    public CompressedContext compress(String codeStructureSummary, List<String> relevantFiles,
                                       String chatHistory, int maxTokens) {
        CompressedContext ctx = new CompressedContext();
        ctx.keyFiles = new ArrayList<>();
        ctx.keySymbols = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        int tokens = 0;

        String[] sections = codeStructureSummary.split("\n类:");
        for (String section : sections) {
            String trimmed = section.trim();
            int sectionTokens = estimateTokens(trimmed);
            if (tokens + sectionTokens > maxTokens / 2) break;
            if (!trimmed.isEmpty()) {
                sb.append("类:").append(trimmed).append("\n");
                tokens += sectionTokens;
            }
        }

        for (String file : relevantFiles) {
            int fileTokens = estimateTokens(file);
            if (tokens + fileTokens < maxTokens / 2) {
                ctx.keyFiles.add(file);
                tokens += fileTokens;
            }
        }

        ctx.summary = sb.toString();
        ctx.estimatedTokens = tokens;
        return ctx;
    }

    public String buildFinalPrompt(String systemPrompt, String compressedContext,
                                    String userQuery, List<String> toolResults) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(systemPrompt).append("\n\n");
        prompt.append("## 项目上下文\n");
        prompt.append(compressedContext).append("\n\n");

        if (!toolResults.isEmpty()) {
            prompt.append("## 工具调用结果\n");
            for (int i = 0; i < toolResults.size(); i++) {
                prompt.append("结果 ").append(i + 1).append(":\n");
                String truncated = l1TruncateToolResult(toolResults.get(i));
                prompt.append(truncated).append("\n\n");
            }
        }

        prompt.append("## 用户请求\n");
        prompt.append(userQuery).append("\n\n");
        prompt.append("请分析需求并执行相应的代码修改，使用工具搜索相关代码后生成修改方案。");

        return prompt.toString();
    }

    String l1TruncateToolResult(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return "";
        }
        String[] lines = toolResult.split("\n", -1);
        if (lines.length <= L1_TOOL_RESULT_MAX_LINES && toolResult.length() <= L1_TOOL_RESULT_MAX_CHARS) {
            return toolResult;
        }

        StringBuilder sb = new StringBuilder();
        int linesToKeep = Math.min(lines.length, L1_TOOL_RESULT_MAX_LINES);
        for (int i = 0; i < linesToKeep; i++) {
            sb.append(lines[i]).append("\n");
        }

        int remaining = lines.length - linesToKeep;
        if (remaining > 0) {
            sb.append("... (L1 截断: 省略 ").append(remaining)
                    .append(" 行, 共 ").append(lines.length).append(" 行)\n");
        }

        String result = sb.toString();
        int hardLimit = L1_TOOL_RESULT_MAX_CHARS * 3;
        if (result.length() > hardLimit) {
            result = result.substring(0, hardLimit)
                    + "\n... (L1 二次截断: 超出字符限制, 共 " + lines.length + " 行)";
        }
        return result;
    }

    public L2CompressionResult checkAndCompressConversation(String conversationId, Path projectPath) {
        int totalMessages = conversationService.getMessages(conversationId).size();
        int rounds = totalMessages / 2;

        if (rounds < L2_CONVERSATION_ROUND_THRESHOLD) {
            return new L2CompressionResult(false, null, 0);
        }

        String summary = generateConversationSummary(conversationId, projectPath);
        int compressedRounds = L2_SUMMARIZE_OLDEST_ROUNDS;
        return new L2CompressionResult(true, summary, compressedRounds);
    }

    private String generateConversationSummary(String conversationId, Path projectPath) {
        var messages = conversationService.getMessages(conversationId);
        if (messages.size() < L2_SUMMARIZE_OLDEST_ROUNDS * 2) {
            return null;
        }

        int endIndex = L2_SUMMARIZE_OLDEST_ROUNDS * 2;
        var oldestMessages = messages.subList(0, Math.min(endIndex, messages.size()));

        StringBuilder summary = new StringBuilder();
        summary.append("## 会话摘要 (L2 压缩)\n");
        summary.append("- 压缩时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        summary.append("- 压缩轮次: 前 ").append(oldestMessages.size() / 2).append(" 轮\n");
        summary.append("- 会话ID: ").append(conversationId).append("\n\n");

        summary.append("### 历史对话摘要\n");
        for (int i = 0; i < oldestMessages.size(); i++) {
            var msg = oldestMessages.get(i);
            String role = "user".equals(msg.getRole()) ? "用户" : "Agent";
            String content = msg.getContent();
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            summary.append("- **").append(role).append("**: ").append(content).append("\n");
        }

        Path memoryPath = projectPath.resolve(".monkeycode").resolve("MEMORY.md");
        try {
            Files.createDirectories(memoryPath.getParent());
            String existingContent = Files.exists(memoryPath)
                    ? Files.readString(memoryPath, StandardCharsets.UTF_8) + "\n"
                    : "# 项目记忆\n\n";
            Files.writeString(memoryPath, existingContent + summary.toString(), StandardCharsets.UTF_8);
            log.info("L2 context summary written to {}", memoryPath);
        } catch (IOException e) {
            log.warn("Failed to write L2 summary to MEMORY.md: {}", e.getMessage());

            Path altPath = projectPath.resolve("MEMORY.md");
            try {
                String existing = Files.exists(altPath)
                        ? Files.readString(altPath, StandardCharsets.UTF_8) + "\n"
                        : "";
                Files.writeString(altPath, existing + summary.toString(), StandardCharsets.UTF_8);
                log.info("L2 context summary written to {}", altPath);
            } catch (IOException ex) {
                log.error("Failed to write L2 summary: {}", ex.getMessage());
            }
        }

        return summary.toString();
    }

    public static class L2CompressionResult {
        public final boolean compressed;
        public final String summary;
        public final int roundsCompressed;

        public L2CompressionResult(boolean compressed, String summary, int roundsCompressed) {
            this.compressed = compressed;
            this.summary = summary;
            this.roundsCompressed = roundsCompressed;
        }
    }

    public int estimateTokens(String text) {
        return text.length() / 3;
    }
}
