package com.example.glmcoder.project;

import com.example.glmcoder.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectManager {

    private final AppProperties appProperties;
    private final ConcurrentMap<String, Path> projects = new ConcurrentHashMap<>();

    public String openProject(String projectPath) throws IOException {
        Path path = Path.of(projectPath).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new IllegalArgumentException("项目路径不存在: " + projectPath);
        }
        String projectId = UUID.randomUUID().toString().substring(0, 8);
        projects.put(projectId, path);
        log.info("Opened project {} -> {}", projectId, path);
        return projectId;
    }

    public Path getProjectPath(String projectId) {
        Path path = projects.get(projectId);
        if (path == null) {
            throw new IllegalArgumentException("项目不存在: " + projectId);
        }
        return path;
    }

    public Path getWorkspaceDir() throws IOException {
        Path dir = Path.of(appProperties.getWorkspace());
        Files.createDirectories(dir);
        return dir;
    }

    public String getProjectPathAsString(String projectId) {
        return getProjectPath(projectId).toString();
    }
}
