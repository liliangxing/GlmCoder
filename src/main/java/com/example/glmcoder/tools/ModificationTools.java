package com.example.glmcoder.tools;

import com.example.glmcoder.security.PathValidator;
import com.example.glmcoder.security.PatchApprovalService;
import com.example.glmcoder.security.PatchApprovalService.PatchEntry;
import com.example.glmcoder.project.ProjectManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModificationTools {

    private final ProjectManager projectManager;
    private final PathValidator pathValidator;
    private final PatchApprovalService patchApprovalService;

    @Tool(name = "editFile", description = "精确修改文件。oldText必须唯一匹配，否则返回匹配位置让你调整")
    public String editFile(
            @ToolParam(description = "文件相对路径") String filePath,
            @ToolParam(description = "要替换的原文本，必须在文件中唯一出现") String oldText,
            @ToolParam(description = "替换后的新文本") String newText,
            @ToolParam(description = "项目ID") String projectId) {

        Path root = projectManager.getProjectPath(projectId);
        Path target = root.resolve(filePath).normalize();

        if (!pathValidator.isAllowed(target, root)) {
            return "错误: 不允许修改该文件";
        }

        if (pathValidator.isProtectedFile(target.getFileName().toString())) {
            return "错误: 该文件受保护，不允许修改";
        }

        try {
            String content = Files.readString(target);

            MatchCheckResult result = checkUniqueMatch(content, oldText);
            if (!result.matched) {
                return result.message;
            }

            int idx = result.matchIndex;
            String modified = content.substring(0, idx) + newText + content.substring(idx + oldText.length());

            PatchEntry patch = new PatchEntry();
            patch.setFilePath(filePath);
            patch.setOperation("edit");
            patch.setOriginalCode(oldText);
            patch.setModifiedCode(newText);
            patch.setDescription("修改文件 " + filePath);
            patch.setApproved(true);
            patchApprovalService.submitPatch(patch);

            Files.writeString(target, modified);
            log.info("Edited file: {}", filePath);
            return "成功修改文件: " + filePath;
        } catch (IOException e) {
            return "错误: " + e.getMessage();
        }
    }

    @Tool(name = "createFile", description = "创建新文件")
    public String createFile(
            @ToolParam(description = "新文件相对路径") String filePath,
            @ToolParam(description = "文件内容") String content,
            @ToolParam(description = "项目ID") String projectId) {

        Path root = projectManager.getProjectPath(projectId);
        Path target = root.resolve(filePath).normalize();

        if (!pathValidator.isAllowed(target, root)) {
            return "错误: 不允许在该位置创建文件";
        }

        try {
            if (Files.exists(target)) {
                return "错误: 文件已存在: " + filePath;
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            log.info("Created file: {}", filePath);
            return "成功创建文件: " + filePath + "\n请调用 compileCheckJava 验证编译。";
        } catch (IOException e) {
            return "错误: " + e.getMessage();
        }
    }

    @Tool(name = "generateDiff", description = "生成当前文件内容与修改后的对比，用于Preview Diff")
    public String generateDiff(
            @ToolParam(description = "文件相对路径") String filePath,
            @ToolParam(description = "修改后的新内容") String modifiedContent,
            @ToolParam(description = "项目ID") String projectId) {

        Path root = projectManager.getProjectPath(projectId);
        Path target = root.resolve(filePath).normalize();

        if (!pathValidator.isAllowed(target, root)) {
            return "错误: 不允许访问该文件";
        }

        try {
            String original = Files.exists(target) ? Files.readString(target) : "";
            String[] origLines = original.split("\n", -1);
            String[] modLines = modifiedContent.split("\n", -1);

            StringBuilder diff = new StringBuilder();
            diff.append("=== Diff: ").append(filePath).append(" ===\n");

            int maxLen = Math.max(origLines.length, modLines.length);
            for (int i = 0; i < maxLen; i++) {
                String orig = i < origLines.length ? origLines[i] : "";
                String mod = i < modLines.length ? modLines[i] : "";

                if (!orig.equals(mod)) {
                    if (!orig.isEmpty()) {
                        diff.append(String.format("-%4d| %s%n", i + 1, orig));
                    }
                    if (!mod.isEmpty()) {
                        diff.append(String.format("+%4d| %s%n", i + 1, mod));
                    }
                }
            }

            if (diff.toString().equals("=== Diff: " + filePath + " ===\n")) {
                return "文件内容无变化";
            }
            return diff.toString();
        } catch (IOException e) {
            return "错误: " + e.getMessage();
        }
    }

    static MatchCheckResult checkUniqueMatch(String content, String oldText) {
        if (!content.contains(oldText)) {
            String normalized = normalizeWhitespace(oldText);
            if (!normalized.equals(oldText) && content.contains(normalized)) {
                return new MatchCheckResult(false, normalized,
                        "错误: oldText 未精确匹配。尝试以下规范化版本（请用此版本重试editFile）:\n```\n"
                                + normalized + "\n```");
            }
            return new MatchCheckResult(false, oldText,
                    "错误: 文件中未找到 oldText。请先用 readFile 重新读取文件内容，确保 oldText 与文件内容完全一致（包括缩进和标点）。");
        }

        int count = 0;
        int idx;
        int firstIdx = -1;
        int searchFrom = 0;
        List<String> matchContexts = new ArrayList<>();

        while ((idx = content.indexOf(oldText, searchFrom)) >= 0) {
            if (count == 0) {
                firstIdx = idx;
            }
            count++;
            if (count <= 5) {
                int lineNum = countLinesBeforeIndex(content, idx);
                int contextStart = Math.max(0, idx - 40);
                int contextEnd = Math.min(content.length(), idx + oldText.length() + 40);
                matchContexts.add(String.format("  位置 %d (行 %d): ...%s...",
                        count, lineNum, content.substring(contextStart, contextEnd).replace("\n", "\\n")));
            }
            searchFrom = idx + 1;
        }

        if (count > 1) {
            StringBuilder sb = new StringBuilder();
            sb.append("错误: oldText 在文件中出现了 ").append(count).append(" 次，不唯一。\n");
            sb.append("请提供更长的上下文来唯一标识要替换的位置。所有匹配位置:\n");
            matchContexts.forEach(sb::append);
            if (count > 5) {
                sb.append("  ... 及其他 ").append(count - 5).append(" 处匹配\n");
            }
            return new MatchCheckResult(false, oldText, sb.toString());
        }

        return new MatchCheckResult(true, oldText, null, firstIdx);
    }

    static String normalizeWhitespace(String text) {
        return text.replace("\r\n", "\n").replace('\t', ' ')
                .replaceAll("[ ]+", " ")
                .replaceAll(" *\n *", "\n")
                .trim();
    }

    static int countLinesBeforeIndex(String content, int index) {
        int lines = 1;
        for (int i = 0; i < index && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    static class MatchCheckResult {
        final boolean matched;
        final String normalizedText;
        final String message;
        final int matchIndex;

        MatchCheckResult(boolean matched, String normalizedText, String message) {
            this(matched, normalizedText, message, -1);
        }

        MatchCheckResult(boolean matched, String normalizedText, String message, int matchIndex) {
            this.matched = matched;
            this.normalizedText = normalizedText;
            this.message = message;
            this.matchIndex = matchIndex;
        }
    }
}
