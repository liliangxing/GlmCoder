package com.example.glmcoder.controller;

import com.example.glmcoder.agent.CodingAgent;
import com.example.glmcoder.attachment.AttachmentManager;
import com.example.glmcoder.index.IndexService;
import com.example.glmcoder.project.ProjectManager;
import com.example.glmcoder.security.PatchApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    private final CodingAgent codingAgent;
    private final ProjectManager projectManager;
    private final IndexService indexService;
    private final AttachmentManager attachmentManager;
    private final PatchApprovalService patchApprovalService;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(
            @RequestParam String projectId,
            @RequestParam String message) {

        try {
            String response = codingAgent.execute(projectId, message);
            return ResponseEntity.ok(Map.of("status", "ok", "response", response));
        } catch (Exception e) {
            log.error("Chat failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestParam String projectId,
            @RequestParam String message) {

        SseEmitter emitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(() -> {
            try {
                String response = codingAgent.executeStreaming(projectId, message);
                emitter.send(SseEmitter.event().data(Map.of("status", "ok", "response", response)));
                emitter.complete();
            } catch (IOException e) {
                try {
                    emitter.send(SseEmitter.event().data(Map.of("status", "error", "message", e.getMessage())));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> indexProject(@RequestParam String projectId) {
        try {
            var path = projectManager.getProjectPath(projectId);
            indexService.indexProject(path);
            var idx = indexService.getStructureIndex();
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "classes", idx.getClassCount(),
                    "methods", idx.getMethodCount()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam MultipartFile file) {
        try {
            var workspace = projectManager.getWorkspaceDir();
            String path = attachmentManager.storeAttachment(file, workspace);
            return ResponseEntity.ok(Map.of("status", "ok", "path", path));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/structure")
    public ResponseEntity<Map<String, String>> getStructure(@RequestParam String projectId) {
        try {
            var path = projectManager.getProjectPath(projectId);
            indexService.indexProject(path);
            String summary = indexService.getStructureIndex().getClassSummary();
            return ResponseEntity.ok(Map.of("status", "ok", "summary", summary));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/patches")
    public ResponseEntity<Map<String, Object>> getPendingPatches() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "patches", patchApprovalService.getPending()
        ));
    }

    @PostMapping("/patches/approve")
    public ResponseEntity<Map<String, String>> approvePatch(@RequestParam String filePath) {
        patchApprovalService.approve(filePath);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/patches/approve-all")
    public ResponseEntity<Map<String, String>> approveAllPatches() {
        patchApprovalService.approveAll();
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
