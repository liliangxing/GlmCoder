package com.example.glmcoder.agent;

import com.example.glmcoder.context.ContextCompressor;
import com.example.glmcoder.index.IndexService;
import com.example.glmcoder.project.ProjectManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodingAgent {

    private final ChatClient chatClient;
    private final ProjectManager projectManager;
    private final IndexService indexService;
    private final ContextCompressor contextCompressor;

    public String execute(String projectId, String userQuery) throws IOException {
        Path projectPath = projectManager.getProjectPath(projectId);

        indexService.indexProject(projectPath);
        String codeSummary = indexService.getStructureIndex().getClassSummary();

        var compressed = contextCompressor.compress(codeSummary, new ArrayList<>(),
                "", contextCompressor.MAX_TOKENS);

        String prompt = """
            ## 项目代码结构
            %s

            ## 用户请求
            %s

            请分析上述请求，使用工具搜索相关代码，制定修改方案并执行。
            修改完成后，调用 compileCheckJava 验证编译是否通过。
            如果编译失败，请根据错误日志自动修复。
            """.formatted(compressed.summary, userQuery);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return response != null ? response : "Agent returned empty response";
        } catch (Exception e) {
            log.error("Agent execution failed", e);
            return "Agent 执行失败: " + e.getMessage();
        }
    }

    public String executeStreaming(String projectId, String userQuery) throws IOException {
        Path projectPath = projectManager.getProjectPath(projectId);
        indexService.indexProject(projectPath);

        String codeSummary = indexService.getStructureIndex().getClassSummary();
        var compressed = contextCompressor.compress(codeSummary, new ArrayList<>(),
                "", contextCompressor.MAX_TOKENS);

        String prompt = """
            ## 项目代码结构
            %s

            ## 用户请求
            %s
            """.formatted(compressed.summary, userQuery);

        StringBuilder result = new StringBuilder();
        chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .doOnNext(content -> result.append(content))
                .blockLast();

        return result.toString();
    }
}
