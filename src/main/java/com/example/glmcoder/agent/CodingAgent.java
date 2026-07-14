package com.example.glmcoder.agent;

import com.example.glmcoder.config.DynamicChatClientFactory;
import com.example.glmcoder.context.ContextCompressor;
import com.example.glmcoder.index.IndexService;
import com.example.glmcoder.project.ProjectManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CodingAgent {

    private final DynamicChatClientFactory chatClientFactory;
    private final ProjectManager projectManager;
    private final IndexService indexService;
    private final ContextCompressor contextCompressor;

    public CodingAgent(DynamicChatClientFactory chatClientFactory,
                       ProjectManager projectManager,
                       IndexService indexService,
                       ContextCompressor contextCompressor) {
        this.chatClientFactory = chatClientFactory;
        this.projectManager = projectManager;
        this.indexService = indexService;
        this.contextCompressor = contextCompressor;
    }

    public String execute(String projectId, String userQuery) throws IOException {
        return execute(projectId, userQuery, "", null);
    }

    public String execute(String projectId, String userQuery, String context, String conversationId) throws IOException {
        Path projectPath = projectManager.getProjectPath(projectId);

        indexService.indexProject(projectPath);
        String codeSummary = indexService.getStructureIndex().getClassSummary();

        var compressed = contextCompressor.compress(codeSummary, new ArrayList<>(),
                "", contextCompressor.MAX_TOKENS);

        String contextSection = context != null && !context.isBlank()
                ? "\n" + context + "\n"
                : "";

        String projectInfo = buildProjectInfo(projectPath);

        String prompt = """
            ## 项目ID
            调用所有工具时，projectId 参数必须使用: %s

            ## 项目信息
            %s

            ## 工具使用规范
            1. 修改文件前先 readFile 确认当前内容
            2. editFile 的 oldText 必须与原文完全一致（含缩进），且必须唯一匹配
            3. 如果 editFile 返回"不唯一"错误，根据返回的行号信息扩大 oldText 范围重新尝试
            4. 如果 editFile 返回"未找到"错误，用 readFile 重新读取文件后再试
            5. 文件创建或修改后，调用 compileCheckJava 验证编译
            6. 编译失败时，根据错误日志定位问题自动修复
            7. 一次只修改与当前任务直接相关的代码，不要重构无关部分

            ## 项目代码结构
            %s

            %s

            ## 用户请求
            %s

            请分析上述请求，使用工具完成任务。完成后调用 compileCheckJava 验证编译通过。
            """.formatted(projectId, projectInfo, compressed.summary, contextSection, userQuery);

        try {
            if (conversationId != null && !conversationId.isBlank()) {
                var l2Result = contextCompressor.checkAndCompressConversation(conversationId, projectPath);
                if (l2Result.compressed) {
                    log.info("L2 compression applied: {} rounds summarized", l2Result.roundsCompressed);
                }
            }

            ChatClient client = conversationId != null && !conversationId.isBlank()
                    ? chatClientFactory.createChatClient(conversationId)
                    : chatClientFactory.createChatClient();

            List<ChatClient> clients = new ArrayList<>();
            clients.add(client);

            String response;
            int maxIterations = 10;
            for (int i = 0; i < maxIterations; i++) {
                response = client.prompt()
                        .user(prompt)
                        .call()
                        .content();
                if (response != null && !response.isBlank()) {
                    return response;
                }
                prompt = "请继续完成任务。如果已完成，回复完成摘要。";
            }
            return "Agent 达到最大迭代次数，任务可能未完成。";
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
        chatClientFactory.createChatClient().prompt()
                .user(prompt)
                .stream()
                .content()
                .doOnNext(result::append)
                .blockLast();

        return result.toString();
    }

    public void executeStreamingToEmitter(String projectId, String userQuery,
                                           String conversationId, SseEmitter emitter)
            throws IOException {
        Path projectPath = projectManager.getProjectPath(projectId);
        indexService.indexProject(projectPath);

        String codeSummary = indexService.getStructureIndex().getClassSummary();
        var compressed = contextCompressor.compress(codeSummary, new ArrayList<>(),
                "", contextCompressor.MAX_TOKENS);

        String prompt = """
            ## 项目ID
            调用所有工具时，projectId 参数必须使用: %s

            ## 项目代码结构
            %s

            ## 用户请求
            %s

            请直接回答用户的问题。如果是编码任务，请立即开始编码并使用工具完成。
            """.formatted(projectId, compressed.summary, userQuery);

        try {
            var client = chatClientFactory.createChatClient(conversationId);
            client.prompt().user(prompt).stream().content()
                    .doOnNext(chunk -> {
                        try {
                            if (chunk != null) {
                                emitter.send(SseEmitter.event().data(chunk));
                            }
                        } catch (IOException e) {
                            log.warn("Failed to send SSE chunk: {}", e.getMessage());
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            emitter.send(SseEmitter.event().data("data:[DONE]"));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(error -> {
                        log.error("Streaming error", error);
                        emitter.completeWithError(error);
                    })
                    .blockLast();
        } catch (Exception e) {
            log.error("Streaming execution failed", e);
            emitter.completeWithError(e);
        }
    }

    private String buildProjectInfo(Path projectPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前日期: ").append(LocalDate.now()).append("\n");
        sb.append("项目根目录: ").append(projectPath).append("\n");

        try {
            Path claudeMd = projectPath.resolve("CLAUDE.md");
            if (Files.exists(claudeMd)) {
                String content = Files.readString(claudeMd, StandardCharsets.UTF_8);
                sb.append("项目约定 (CLAUDE.md):\n```\n")
                        .append(content.length() > 2000 ? content.substring(0, 2000) + "..." : content)
                        .append("\n```\n");
            }
        } catch (IOException e) {
            log.debug("Cannot read CLAUDE.md: {}", e.getMessage());
        }

        try {
            Path memPath = projectPath.resolve("MEMORY.md");
            if (Files.exists(memPath)) {
                String content = Files.readString(memPath, StandardCharsets.UTF_8);
                sb.append("会话记忆 (MEMORY.md):\n```\n")
                        .append(content.length() > 1500 ? content.substring(0, 1500) + "..." : content)
                        .append("\n```\n");
            }
        } catch (IOException e) {
            log.debug("Cannot read MEMORY.md: {}", e.getMessage());
        }

        return sb.toString();
    }
}
