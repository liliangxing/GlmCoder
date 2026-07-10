package com.example.glmcoder.controller;

import com.example.glmcoder.config.ModelConfigService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/ui")
public class ModelConfigController {

    private final ModelConfigService configService;

    public ModelConfigController(ModelConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/config")
    public Map<String, String> getConfig() {
        return Map.of(
                "model", configService.getModel(),
                "apiKey", maskKey(configService.getApiKey())
        );
    }

    @PostMapping("/config")
    public Map<String, String> updateConfig(
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String apiKey) {

        if (model != null && !model.isBlank()) {
            configService.setModel(model.trim());
        }
        if (apiKey != null && !apiKey.isBlank()) {
            configService.setApiKey(apiKey.trim());
        }

        return Map.of(
                "status", "ok",
                "model", configService.getModel(),
                "apiKey", maskKey(configService.getApiKey())
        );
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
