package com.example.glmcoder.tools;

import com.example.glmcoder.security.PathValidator;
import com.example.glmcoder.security.PatchApprovalService;
import com.example.glmcoder.project.ProjectManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModificationTools {

    private final ProjectManager projectManager;
    private final PathValidator pathValidator;
    private final PatchApprovalService patchApprovalService;

    @Tool(name = "editFile", description = "修改指定文件内容。使用指定行号范围的精确文本替换")
    public String editFile(
            @ToolParam(description = "文件相对路径") String filePath,
            @ToolParam(description = "要替换的原文本") String oldText,
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
            if (!content.contains(oldText)) {
                return "错误: 文件中未找到指定的原文本";
            }
            String modified = content.replace(oldText, newText);

            var patch = new PatchApprovalService.PatchEntry();
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
            return "成功创建文件: " + filePath;
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
}
