package com.example.glmcoder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "glmcoder")
public class AppProperties {
    private String workspace = System.getProperty("user.home") + "/glmcoder-workspace";
    private int maxRetries = 3;
    private Index index = new Index();

    @Data
    public static class Index {
        private int maxFiles = 10000;
    }
}
