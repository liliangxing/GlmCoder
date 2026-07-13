package com.example.glmcoder.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
public class BuildTools {

    private static final Set<String> DANGEROUS_COMMAND_PATTERNS = Set.of(
            "rm -rf", "rm -r ", "rm -fr", "shred", "unlink",
            "chmod 777", "chown root", "chown 0:",
            "> /dev/sd", "mkfs", "fdisk", "parted", "dd if=",
            "git push --force", "git push -f", "git reset --hard",
            "DROP TABLE", "DROP DATABASE", "TRUNCATE TABLE",
            "docker rm", "docker rmi", "docker system prune",
            "iptables", "ufw", "firewall-cmd",
            "shutdown", "reboot", "poweroff", "halt",
            "kill -9", "pkill", "killall",
            "chmod -R", "chown -R"
    );

    private static final int PROCESS_TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_LINES = 500;

    static String validateCommand(List<String> command) {
        String fullCmd = String.join(" ", command);
        for (String pattern : DANGEROUS_COMMAND_PATTERNS) {
            if (fullCmd.contains(pattern)) {
                return "拒绝执行: 命令包含危险操作 (" + pattern + ")。该命令可能造成数据丢失或系统损坏。";
            }
        }
        return null;
    }

    static String executeProcess(File workDir, List<String> command, int timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return "命令执行超时 (" + timeoutSeconds + "秒)，已被终止。";
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();

            String truncated = truncateOutput(output, MAX_OUTPUT_LINES);
            StringBuilder result = new StringBuilder();
            if (exitCode == 0) {
                result.append("命令执行成功 (exit=0)");
            } else {
                result.append("命令执行失败 (exit=").append(exitCode).append(")");
            }
            if (!truncated.isBlank()) {
                result.append("\n").append(truncated);
            }
            return result.toString();
        } catch (IOException e) {
            return "命令执行出错: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "命令执行被中断";
        }
    }

    static String truncateOutput(String output, int maxLines) {
        if (output == null || output.isBlank()) {
            return "";
        }
        String[] lines = output.split("\n", -1);
        if (lines.length <= maxLines) {
            return output;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            sb.append(lines[i]).append("\n");
        }
        sb.append("... (输出被截断，共 ").append(lines.length).append(" 行，仅显示前 ").append(maxLines).append(" 行)");
        return sb.toString();
    }

    @Tool(name = "compileCheckJava", description = "在指定目录执行 Maven 编译检查。返回编译结果，如果有错误会列出错误信息")
    public String compileCheckJava(
            @ToolParam(description = "项目根目录路径", required = false) String projectDir) {

        String dir = (projectDir != null && !projectDir.isBlank()) ? projectDir : "/workspace/GlmCoder";
        return executeProcess(new File(dir), List.of("mvn", "compile", "-q"), PROCESS_TIMEOUT_SECONDS);
    }

    @Tool(name = "runTests", description = "运行项目中与给定模式匹配的测试")
    public String runTests(
            @ToolParam(description = "测试类或测试方法匹配模式，如 \"**/AgentTest*\"", required = false) String testPattern,
            @ToolParam(description = "项目根目录路径", required = false) String projectDir) {

        String dir = (projectDir != null && !projectDir.isBlank()) ? projectDir : "/workspace/GlmCoder";

        List<String> cmd;
        if (testPattern != null && !testPattern.isBlank()) {
            cmd = List.of("mvn", "test", "-Dtest=" + testPattern, "-q");
        } else {
            cmd = List.of("mvn", "test", "-q");
        }
        return executeProcess(new File(dir), cmd, PROCESS_TIMEOUT_SECONDS);
    }

    @Tool(name = "executeBash", description = "执行安全的 Shell 命令并返回输出。危险命令会被自动拦截。")
    public String executeBash(
            @ToolParam(description = "要执行的 Shell 命令字符串") String command,
            @ToolParam(description = "工作目录，默认为项目根目录", required = false) String workDir) {

        if (command == null || command.isBlank()) {
            return "错误: 命令不能为空";
        }

        String dir = (workDir != null && !workDir.isBlank()) ? workDir : "/workspace/GlmCoder";
        File wd = new File(dir);
        if (!wd.exists() || !wd.isDirectory()) {
            return "错误: 目录不存在: " + dir;
        }

        List<String> cmdList = List.of("bash", "-c", command);
        String validation = validateCommand(cmdList);
        if (validation != null) {
            log.warn("Blocked dangerous command: {}", command);
            return validation;
        }

        return executeProcess(wd, cmdList, PROCESS_TIMEOUT_SECONDS);
    }
}
