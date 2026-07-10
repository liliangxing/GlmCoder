package com.example.glmcoder.config;

import org.springframework.stereotype.Service;

@Service
public class ModelConfigService {

    private volatile String currentModel = "glm-4-flash";

    private volatile String currentApiKey = "15085dae9c11401da6662b88c91d2f4c.AiOP4uJyhV8WaMzA";

    private final String baseUrl = "https://open.bigmodel.cn/api/paas";

    private final String completionsPath = "/v4/chat/completions";

    public String getModel() {
        return currentModel;
    }

    public String getApiKey() {
        return currentApiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getCompletionsPath() {
        return completionsPath;
    }

    public void setModel(String model) {
        this.currentModel = model;
    }

    public void setApiKey(String apiKey) {
        this.currentApiKey = apiKey;
    }
}
