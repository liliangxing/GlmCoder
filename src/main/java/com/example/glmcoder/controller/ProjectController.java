package com.example.glmcoder.controller;

import com.example.glmcoder.project.ProjectManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Controller
@RequestMapping("/ui")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectManager projectManager;

    @GetMapping
    public String index() {
        return "index";
    }

    @PostMapping("/project/open")
    @ResponseBody
    public Map<String, String> openProject(@RequestParam String path) {
        try {
            String id = projectManager.openProject(path);
            return Map.of("status", "ok", "projectId", id);
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    @GetMapping("/project/info")
    @ResponseBody
    public Map<String, String> projectInfo(@RequestParam String projectId) {
        try {
            String path = projectManager.getProjectPathAsString(projectId);
            return Map.of("status", "ok", "path", path);
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}
