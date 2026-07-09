package com.example.glmcoder.context;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ContextCompressor {

    public static final int MAX_TOKENS = 8000;

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
        String firstSection = sections.length > 0 ? sections[0] : "";

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
                String result = truncateIfNeeded(toolResults.get(i), 2000);
                prompt.append(result).append("\n\n");
            }
        }

        prompt.append("## 用户请求\n");
        prompt.append(userQuery).append("\n\n");
        prompt.append("请分析需求并执行相应的代码修改，使用工具搜索相关代码后生成修改方案。");

        return prompt.toString();
    }

    private String truncateIfNeeded(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n... (truncated, total " + text.length() + " chars)";
    }

    public int estimateTokens(String text) {
        return text.length() / 3;
    }
}
