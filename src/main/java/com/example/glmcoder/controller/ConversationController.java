package com.example.glmcoder.controller;

import com.example.glmcoder.model.Conversation;
import com.example.glmcoder.model.ConversationMessage;
import com.example.glmcoder.service.ConversationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public Map<String, Object> listConversations(
            @RequestParam String projectId,
            @RequestParam(required = false) String keyword) {
        List<Conversation> list = conversationService.searchConversations(projectId, keyword);
        return Map.of("status", "ok", "conversations", list);
    }

    @PostMapping
    public Map<String, Object> createConversation(
            @RequestParam String projectId,
            @RequestParam(required = false, defaultValue = "新对话") String message) {
        Conversation conv = conversationService.createConversation(projectId, message);
        return Map.of("status", "ok", "conversation", conv);
    }

    @GetMapping("/{id}/messages")
    public Map<String, Object> getMessages(@PathVariable String id) {
        List<ConversationMessage> messages = conversationService.getMessages(id);
        return Map.of("status", "ok", "messages", messages);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteConversation(@PathVariable String id) {
        conversationService.deleteConversation(id);
        return Map.of("status", "ok");
    }
}
