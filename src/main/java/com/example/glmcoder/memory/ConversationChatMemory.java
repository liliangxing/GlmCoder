package com.example.glmcoder.memory;

import com.example.glmcoder.model.ConversationMessage;
import com.example.glmcoder.service.ConversationService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Primary
public class ConversationChatMemory implements ChatMemory {

    private final ConversationService conversationService;

    public ConversationChatMemory(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        for (Message msg : messages) {
            conversationService.saveMessage(
                    conversationId,
                    msg.getMessageType() == MessageType.USER ? "user" : "agent",
                    msg.getText()
            );
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        List<ConversationMessage> msgs = conversationService.getMessages(conversationId);
        List<Message> result = new ArrayList<>();
        for (var cm : msgs) {
            if ("user".equals(cm.getRole())) {
                result.add(new UserMessage(cm.getContent()));
            } else {
                result.add(new AssistantMessage(cm.getContent()));
            }
        }
        return result;
    }

    @Override
    public void clear(String conversationId) {
        conversationService.deleteConversation(conversationId);
    }
}
