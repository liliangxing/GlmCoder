package com.example.glmcoder.tools;

import com.example.glmcoder.index.CodeStructureIndex;
import com.example.glmcoder.project.ProjectManager;
import com.example.glmcoder.security.PathValidator;
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
public class CodeUnderstandingTools {

    private final CodeStructureIndex codeStructureIndex;
    private final ProjectManager projectManager;
    private final PathValidator pathValidator;

    @Tool(name = "searchCode", description = "搜索项目中的类或方法定义，返回匹配的结构信息")
    public String searchCode(
            @ToolParam(description = "要搜索的关键词（类名或方法名）") String keyword,
            @ToolParam(description = "搜索类型: class 或 method", required = false) String type,
            @ToolParam(description = "项目ID，必填") String projectId) {

        if (projectId == null || projectId.isBlank()) {
            return "错误: 必须提供 projectId";
        }

        StringBuilder result = new StringBuilder();

        if (type == null || type.equals("class")) {
            var classes = codeStructureIndex.searchClasses(keyword);
            if (!classes.isEmpty()) {
                result.append("=== 找到 ").append(classes.size()).append(" 个类 ===\n");
                for (var cls : classes) {
                    result.append("类: ").append(cls.getPackageName()).append(".").append(cls.getName()).append("\n");
                }
            }
        }

        if (type == null || type.equals("method")) {
            var methods = codeStructureIndex.searchMethods(keyword);
            if (!methods.isEmpty()) {
                result.append("\n=== 找到 ").append(methods.size()).append(" 个方法 ===\n");
                for (var m : methods) {
                    result.append(m.getSignature()).append("\n");
                }
            }
        }

        if (result.isEmpty()) {
            return "未找到匹配 \"" + keyword + "\" 的类或方法";
        }
        return result.toString();
    }

    @Tool(name = "readFile", description = "读取指定文件的完整内容")
    public String readFile(
            @ToolParam(description = "文件相对路径") String filePath,
            @ToolParam(description = "项目ID") String projectId) {

        Path root = projectManager.getProjectPath(projectId);
        Path target = root.resolve(filePath).normalize();

        if (!pathValidator.isAllowed(target, root)) {
            return "错误: 不允许访问该文件: " + filePath;
        }

        try {
            String content = Files.readString(target);
            String[] lines = content.split("\n");
            StringBuilder sb = new StringBuilder();
            sb.append("=== ").append(filePath).append(" ===\n");
            for (int i = 0; i < lines.length; i++) {
                sb.append(String.format("%4d| %s%n", i + 1, lines[i]));
            }
            return sb.toString();
        } catch (IOException e) {
            return "错误: 无法读取 " + filePath + ": " + e.getMessage();
        }
    }

    @Tool(name = "getClassStructure", description = "获取指定类的完整结构，包括字段和方法")
    public String getClassStructure(
            @ToolParam(description = "类名") String className,
            @ToolParam(description = "项目ID") String projectId) {

        var classInfo = codeStructureIndex.getClassStructure(className);
        if (classInfo == null) {
            return "未找到类: " + className;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("类: ").append(classInfo.getPackageName()).append(".").append(classInfo.getName()).append("\n");
        sb.append("文件: ").append(classInfo.getFilePath()).append("\n");

        if (!classInfo.getFields().isEmpty()) {
            sb.append("\n字段:\n");
            for (String field : classInfo.getFields()) {
                sb.append("  ").append(field).append("\n");
            }
        }

        sb.append("\n方法:\n");
        codeStructureIndex.searchMethods(classInfo.getName()).forEach(m ->
            sb.append("  ").append(m.getSignature()).append("\n")
        );

        return sb.toString();
    }
}
