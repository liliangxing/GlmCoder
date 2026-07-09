package com.example.glmcoder.tools;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileTools {

    private final ProjectManager projectManager;
    private final PathValidator pathValidator;

    @Tool(name = "listFiles", description = "列出项目指定目录的文件")
    public String listFiles(
            @ToolParam(description = "目录相对路径，根目录用 \".\"") String dirPath,
            @ToolParam(description = "项目ID") String projectId) {

        Path root = projectManager.getProjectPath(projectId);
        Path dir = root.resolve(dirPath).normalize();

        if (!pathValidator.isAllowed(dir, root)) {
            return "错误: 不允许访问该目录";
        }

        try (Stream<Path> stream = Files.list(dir)) {
            return stream.sorted()
                    .map(p -> (Files.isDirectory(p) ? "[DIR]  " : "[FILE] ") + root.relativize(p))
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "错误: " + e.getMessage();
        }
    }

    @Tool(name = "searchFiles", description = "根据glob模式搜索文件")
    public String searchFiles(
            @ToolParam(description = "glob模式，如 \"src/**/*.java\"") String pattern,
            @ToolParam(description = "项目ID") String projectId) {

        Path root = projectManager.getProjectPath(projectId);

        try (Stream<Path> stream = Files.walk(root)) {
            var matcher = root.getFileSystem().getPathMatcher("glob:" + pattern);
            var results = stream
                    .filter(matcher::matches)
                    .filter(p -> pathValidator.isAllowed(p, root))
                    .map(p -> root.relativize(p).toString())
                    .limit(50)
                    .collect(Collectors.toList());

            if (results.isEmpty()) {
                return "未找到匹配 \"" + pattern + "\" 的文件";
            }
            return "找到 " + results.size() + " 个文件:\n" + String.join("\n", results);
        } catch (IOException e) {
            return "错误: " + e.getMessage();
        }
    }

    @Tool(name = "getDependencies", description = "分析指定文件的import依赖")
    public String getDependencies(
            @ToolParam(description = "文件相对路径") String filePath,
            @ToolParam(description = "项目ID") String projectId) {

        Path root = projectManager.getProjectPath(projectId);
        Path target = root.resolve(filePath).normalize();

        if (!pathValidator.isAllowed(target, root)) {
            return "错误: 不允许访问该文件";
        }

        try {
            String content = Files.readString(target);
            var imports = content.lines()
                    .filter(line -> line.trim().startsWith("import "))
                    .map(String::trim)
                    .collect(Collectors.toList());

            var internal = imports.stream()
                    .filter(i -> i.contains("com.example.") || i.contains("glmcoder."))
                    .collect(Collectors.toList());
            var external = imports.stream()
                    .filter(i -> !internal.contains(i))
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("=== 依赖分析: ").append(filePath).append(" ===\n");
            if (!internal.isEmpty()) {
                sb.append("\n内部依赖:\n");
                internal.forEach(i -> sb.append("  ").append(i).append("\n"));
            }
            if (!external.isEmpty()) {
                sb.append("\n外部依赖:\n");
                external.forEach(i -> sb.append("  ").append(i).append("\n"));
            }
            return sb.toString();
        } catch (IOException e) {
            return "错误: " + e.getMessage();
        }
    }
}
