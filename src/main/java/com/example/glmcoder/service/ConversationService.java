package com.example.glmcoder.service;

import com.example.glmcoder.model.Conversation;
import com.example.glmcoder.model.ConversationMessage;
import com.example.glmcoder.repository.ConversationMessageRepository;
import com.example.glmcoder.repository.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepo;
    private final ConversationMessageRepository messageRepo;

    public ConversationService(ConversationRepository conversationRepo,
                                ConversationMessageRepository messageRepo) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
    }

    public List<Conversation> listConversations(String projectId) {
        return conversationRepo.findByProjectIdOrderByUpdatedAtDesc(projectId);
    }

    public List<Conversation> searchConversations(String projectId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listConversations(projectId);
        }
        return conversationRepo.searchByKeyword(projectId, keyword.trim());
    }

    @Transactional
    public Conversation createConversation(String projectId, String firstMessage) {
        Conversation conv = new Conversation();
        conv.setProjectId(projectId);
        String title = firstMessage.length() > 50
                ? firstMessage.substring(0, 50) + "..."
                : firstMessage;
        conv.setTitle(title);
        conv = conversationRepo.save(conv);

        ConversationMessage msg = new ConversationMessage();
        msg.setConversationId(conv.getId());
        msg.setRole("user");
        msg.setContent(firstMessage);
        messageRepo.save(msg);

        return conv;
    }

    @Transactional
    public ConversationMessage saveMessage(String conversationId, String role, String content) {
        ConversationMessage msg = new ConversationMessage();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        ConversationMessage saved = messageRepo.save(msg);

        conversationRepo.findById(conversationId).ifPresent(conv -> {
            if ("user".equals(role) && messageRepo.countByConversationId(conversationId) <= 2) {
                String title = content.length() > 50
                        ? content.substring(0, 50) + "..."
                        : content;
                conv.setTitle(title);
            }
            conversationRepo.save(conv);
        });

        return saved;
    }

    public List<ConversationMessage> getMessages(String conversationId) {
        return messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional
    public void deleteConversation(String conversationId) {
        messageRepo.deleteAll(messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId));
        conversationRepo.deleteById(conversationId);
    }
}
