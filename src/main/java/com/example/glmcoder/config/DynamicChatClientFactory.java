package com.example.glmcoder.config;

import com.example.glmcoder.tools.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DynamicChatClientFactory {

    private final ModelConfigService config;
    private final CodeUnderstandingTools codeUnderstandingTools;
    private final FileTools fileTools;
    private final ModificationTools modificationTools;
    private final BuildTools buildTools;
    private final DependencyAnalysisTools dependencyAnalysisTools;

    public DynamicChatClientFactory(ModelConfigService config,
                                     CodeUnderstandingTools codeUnderstandingTools,
                                     FileTools fileTools,
                                     ModificationTools modificationTools,
                                     BuildTools buildTools,
                                     DependencyAnalysisTools dependencyAnalysisTools) {
        this.config = config;
        this.codeUnderstandingTools = codeUnderstandingTools;
        this.fileTools = fileTools;
        this.modificationTools = modificationTools;
        this.buildTools = buildTools;
        this.dependencyAnalysisTools = dependencyAnalysisTools;
    }

    public ChatClient createChatClient() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .completionsPath(config.getCompletionsPath())
                .restClientBuilder(RestClient.builder())
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(config.getModel())
                        .temperature(0.1)
                        .build())
                .build();

        return ChatClient.builder(chatModel)
                .defaultTools(
                        codeUnderstandingTools,
                        fileTools,
                        modificationTools,
                        buildTools,
                        dependencyAnalysisTools
                )
                .build();
    }
}
