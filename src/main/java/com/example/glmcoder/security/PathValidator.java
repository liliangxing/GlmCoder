package com.example.glmcoder.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PathValidator {

    private static final Set<String> PROTECTED_FILES = Set.of(
            "pom.xml", "build.gradle", "application.yml", "application.properties",
            ".gitignore", "Dockerfile", "docker-compose.yml"
    );

    private static final Set<String> PROTECTED_DIRS = Set.of(
            ".git", "target", "build", "node_modules", ".idea"
    );

    public boolean isAllowed(Path targetPath, Path projectRoot) {
        Path normalized = targetPath.toAbsolutePath().normalize();
        Path root = projectRoot.toAbsolutePath().normalize();

        if (!normalized.startsWith(root)) {
            log.warn("Path outside project: {}", normalized);
            return false;
        }

        String fileName = normalized.getFileName().toString();
        if (isProtectedFile(fileName)) {
            log.warn("Access to protected file: {}", normalized);
            return false;
        }

        for (int i = normalized.getNameCount() - 1; i >= 0; i--) {
            if (PROTECTED_DIRS.contains(normalized.getName(i).toString())) {
                log.warn("Access to protected dir: {}", normalized);
                return false;
            }
        }

        return true;
    }

    public boolean isProtectedFile(String fileName) {
        String lower = fileName.toLowerCase();
        return PROTECTED_FILES.stream().anyMatch(lower::equals);
    }

    public boolean isDeleteProtected(String fileName) {
        return isProtectedFile(fileName);
    }
}
