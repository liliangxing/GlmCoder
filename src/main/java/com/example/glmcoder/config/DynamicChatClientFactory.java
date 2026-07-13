package com.example.glmcoder.config;

import com.example.glmcoder.tools.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DynamicChatClientFactory {

    private final ModelConfigService config;
    private final ChatMemory chatMemory;
    private final CodeUnderstandingTools codeUnderstandingTools;
    private final FileTools fileTools;
    private final ModificationTools modificationTools;
    private final BuildTools buildTools;
    private final DependencyAnalysisTools dependencyAnalysisTools;

    public DynamicChatClientFactory(ModelConfigService config,
                                     ChatMemory chatMemory,
                                     CodeUnderstandingTools codeUnderstandingTools,
                                     FileTools fileTools,
                                     ModificationTools modificationTools,
                                     BuildTools buildTools,
                                     DependencyAnalysisTools dependencyAnalysisTools) {
        this.config = config;
        this.chatMemory = chatMemory;
        this.codeUnderstandingTools = codeUnderstandingTools;
        this.fileTools = fileTools;
        this.modificationTools = modificationTools;
        this.buildTools = buildTools;
        this.dependencyAnalysisTools = dependencyAnalysisTools;
    }

    public ChatClient createChatClient() {
        return buildChatClientBuilder().build();
    }

    public ChatClient createChatClient(String conversationId) {
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(conversationId)
                .build();
        return buildChatClientBuilder()
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    private ChatClient.Builder buildChatClientBuilder() {
        ZhiPuAiApi api = ZhiPuAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .restClientBuilder(RestClient.builder())
                .build();

        ZhiPuAiChatModel chatModel = new ZhiPuAiChatModel(api,
                ZhiPuAiChatOptions.builder()
                        .model(config.getModel())
                        .temperature(0.1)
                        .toolChoice("auto")
                        .build());

        return ChatClient.builder(chatModel)
                .defaultTools(
                        codeUnderstandingTools,
                        fileTools,
                        modificationTools,
                        buildTools,
                        dependencyAnalysisTools
                )
                .defaultAdvisors(new SimpleLoggerAdvisor());
    }
}
