package com.example.glmcoder.controller;

import com.example.glmcoder.agent.CodingAgent;
import com.example.glmcoder.attachment.AttachmentManager;
import com.example.glmcoder.config.DynamicChatClientFactory;
import com.example.glmcoder.index.IndexService;
import com.example.glmcoder.model.ConversationMessage;
import com.example.glmcoder.project.ProjectManager;
import com.example.glmcoder.security.PatchApprovalService;
import com.example.glmcoder.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api")
public class AgentController {

    private final CodingAgent codingAgent;
    private final ProjectManager projectManager;
    private final IndexService indexService;
    private final AttachmentManager attachmentManager;
    private final PatchApprovalService patchApprovalService;
    private final DynamicChatClientFactory chatClientFactory;
    private final ConversationService conversationService;

    public AgentController(CodingAgent codingAgent, ProjectManager projectManager,
                           IndexService indexService, AttachmentManager attachmentManager,
                           PatchApprovalService patchApprovalService,
                           DynamicChatClientFactory chatClientFactory,
                           ConversationService conversationService) {
        this.codingAgent = codingAgent;
        this.projectManager = projectManager;
        this.indexService = indexService;
        this.attachmentManager = attachmentManager;
        this.patchApprovalService = patchApprovalService;
        this.chatClientFactory = chatClientFactory;
        this.conversationService = conversationService;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestParam String projectId,
            @RequestParam String message,
            @RequestParam(required = false) String conversationId) {

        try {
            if (conversationId == null || conversationId.isBlank()) {
                var conv = conversationService.createConversation(projectId, message);
                conversationId = conv.getId();
            }

            String response = codingAgent.execute(projectId, message, "", conversationId);

            conversationService.saveMessage(conversationId, "agent", response);

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "response", response,
                    "conversationId", conversationId
            ));
        } catch (Exception e) {
            log.error("Chat failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        try {
            String response = chatClientFactory.createChatClient()
                    .prompt()
                    .user("用一个简短的句子回答：什么是 Java")
                    .call()
                    .content();
            return ResponseEntity.ok(Map.of("status", "ok", "response", response));
        } catch (Exception e) {
            log.error("Ping failed", e);
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
