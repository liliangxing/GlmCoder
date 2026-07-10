package com.example.glmcoder.agent;

import com.example.glmcoder.config.DynamicChatClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ReflectionAgent {

    private final DynamicChatClientFactory chatClientFactory;

    public ReflectionAgent(DynamicChatClientFactory chatClientFactory) {
        this.chatClientFactory = chatClientFactory;
    }

    public static final int MAX_RETRIES = 3;

    public static class ReflectionResult {
        public boolean success;
        public String summary;
        public List<AttemptRecord> attempts = new ArrayList<>();

        public static class AttemptRecord {
            public int attempt;
            public String toolCalled;
            public String result;
            public boolean passed;
        }
    }

    public ReflectionResult reflectAndFix(String projectPath, String task,
                                           String lastOutput, int currentAttempt) {
        ReflectionResult reflection = new ReflectionResult();

        if (currentAttempt > MAX_RETRIES) {
            reflection.success = false;
            reflection.summary = "超过最大重试次数(" + MAX_RETRIES + ")，操作终止";
            return reflection;
        }

        boolean compileOk = checkCompile(projectPath);
        if (compileOk) {
            reflection.success = true;
            reflection.summary = "编译检查通过，任务完成";
            return reflection;
        }

        String compileError = getCompileError(projectPath);
        String feedback = chatClientFactory.createChatClient().prompt()
                .user("""
                    代码修改后编译失败：
                    %s

                    请分析错误原因并修复。
                    原任务: %s
                    """.formatted(compileError, task))
                .call()
                .content();

        reflection.success = false;
        reflection.summary = feedback != null ? feedback : "无法自动修复";
        return reflection;
    }

    private boolean checkCompile(String projectPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "compile", "-q");
            pb.directory(new File(projectPath));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String getCompileError(String projectPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "compile");
            pb.directory(new File(projectPath));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            return output.contains("ERROR") ? output : "编译失败，详见 Maven 输出";
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "无法获取编译错误: " + e.getMessage();
        }
    }
}
