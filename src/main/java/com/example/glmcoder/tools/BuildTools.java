package com.example.glmcoder.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Slf4j
@Component
public class BuildTools {

    @Tool(name = "compileCheckJava", description = "在指定目录执行 Maven 编译检查。返回编译结果，如果有错误会列出错误信息")
    public String compileCheckJava(
            @ToolParam(description = "项目根目录路径", required = false) String projectDir) {

        String dir = (projectDir != null && !projectDir.isBlank()) ? projectDir : "/workspace/GlmCoder";

        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "compile", "-q");
            pb.directory(new File(dir));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                String result = "编译成功!";
                if (!output.isBlank()) {
                    result += "\n" + output;
                }
                return result;
            } else {
                return "编译失败:\n" + output;
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "编译检查出错: " + e.getMessage();
        }
    }

    @Tool(name = "runTests", description = "运行项目中与给定模式匹配的测试")
    public String runTests(
            @ToolParam(description = "测试类或测试方法匹配模式，如 \"**/AgentTest*\"", required = false) String testPattern,
            @ToolParam(description = "项目根目录路径", required = false) String projectDir) {

        String dir = (projectDir != null && !projectDir.isBlank()) ? projectDir : "/workspace/GlmCoder";

        try {
            ProcessBuilder pb;
            if (testPattern != null && !testPattern.isBlank()) {
                pb = new ProcessBuilder("mvn", "test", "-Dtest=" + testPattern, "-q");
            } else {
                pb = new ProcessBuilder("mvn", "test", "-q");
            }
            pb.directory(new File(dir));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return "测试全部通过!";
            } else {
                return "测试失败:\n" + output;
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "测试执行出错: " + e.getMessage();
        }
    }
}
