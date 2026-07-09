package com.example.glmcoder.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DependencyAnalysisTools {

    @Tool(name = "analyzeDependencies", description = "分析项目中模块间的依赖关系，检测循环依赖")
    public String analyzeDependencies(
            @ToolParam(description = "项目根目录路径") String projectDir) {

        Path root = Path.of(projectDir);
        Map<String, Set<String>> graph = new HashMap<>();

        try {
            Files.walk(root)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            String packageName = content.lines()
                                    .filter(l -> l.trim().startsWith("package "))
                                    .findFirst()
                                    .map(l -> l.replace("package ", "").replace(";", "").trim())
                                    .orElse("");

                            Set<String> deps = content.lines()
                                    .filter(l -> l.trim().startsWith("import "))
                                    .map(l -> l.replace("import ", "").replace(";", "").trim())
                                    .collect(Collectors.toSet());

                            graph.computeIfAbsent(packageName, k -> new HashSet<>()).addAll(deps);
                        } catch (IOException ignored) {}
                    });

            StringBuilder result = new StringBuilder("=== 依赖分析结果 ===\n");
            result.append("包数量: ").append(graph.size()).append("\n\n");

            Set<String> cycles = findCycles(graph);
            if (!cycles.isEmpty()) {
                result.append("检测到循环依赖:\n");
                cycles.forEach(c -> result.append("  ").append(c).append("\n"));
            } else {
                result.append("未检测到循环依赖\n");
            }

            return result.toString();
        } catch (IOException e) {
            return "依赖分析错误: " + e.getMessage();
        }
    }

    private Set<String> findCycles(Map<String, Set<String>> graph) {
        Set<String> cycles = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (String node : graph.keySet()) {
            dfs(node, graph, visited, inStack, new ArrayList<>(), cycles);
        }
        return cycles;
    }

    private void dfs(String node, Map<String, Set<String>> graph,
                     Set<String> visited, Set<String> inStack,
                     List<String> path, Set<String> cycles) {
        if (inStack.contains(node)) {
            int idx = path.indexOf(node);
            if (idx >= 0) {
                cycles.add(String.join(" -> ", path.subList(idx, path.size())));
            }
            return;
        }
        if (visited.contains(node)) return;

        visited.add(node);
        inStack.add(node);
        path.add(node);

        for (String neighbor : graph.getOrDefault(node, Collections.emptySet())) {
            dfs(neighbor, graph, visited, inStack, path, cycles);
        }

        path.remove(path.size() - 1);
        inStack.remove(node);
    }
}
