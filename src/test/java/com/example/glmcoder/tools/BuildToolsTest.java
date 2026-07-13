package com.example.glmcoder.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildToolsTest {

    @Test
    @DisplayName("Dangerous command: rm -rf blocked")
    void blockRmRf() {
        String result = BuildTools.validateCommand(List.of("bash", "-c", "rm -rf /"));
        assertNotNull(result);
        assertTrue(result.contains("rm -rf"));
        assertTrue(result.contains("拒绝执行"));
    }

    @Test
    @DisplayName("Dangerous command: chmod 777 blocked")
    void blockChmod777() {
        String result = BuildTools.validateCommand(List.of("bash", "-c", "chmod 777 /var/www"));
        assertNotNull(result);
        assertTrue(result.contains("chmod 777"));
    }

    @Test
    @DisplayName("Dangerous command: git push --force blocked")
    void blockGitPushForce() {
        String result = BuildTools.validateCommand(List.of("bash", "-c", "git push --force origin"));
        assertNotNull(result);
        assertTrue(result.contains("git push --force"));
    }

    @Test
    @DisplayName("Dangerous command: dd if=/dev/zero blocked")
    void blockDdBlock() {
        String result = BuildTools.validateCommand(List.of("bash", "-c", "dd if=/dev/zero of=/dev/sda"));
        assertNotNull(result);
        assertTrue(result.contains("dd if="));
    }

    @Test
    @DisplayName("Safe command: ls -la allowed")
    void allowSafeCommand() {
        String result = BuildTools.validateCommand(List.of("bash", "-c", "ls -la"));
        assertEquals(null, result);
    }

    @Test
    @DisplayName("Safe command: echo hello allowed")
    void allowSafeEcho() {
        String result = BuildTools.validateCommand(List.of("bash", "-c", "echo hello"));
        assertEquals(null, result);
    }

    @Test
    @DisplayName("exec bash rejects null command")
    void rejectNullCommand() {
        var tools = new BuildTools();
        String result = tools.executeBash(null, null);
        assertTrue(result.contains("命令不能为空"));
    }

    @Test
    @DisplayName("exec bash rejects non-existent directory")
    void rejectInvalidDir() {
        var tools = new BuildTools();
        String result = tools.executeBash("ls", "/nonexistent/dir");
        assertTrue(result.contains("目录不存在"));
    }

    @Test
    @DisplayName("exec bash runs safe command correctly")
    void safeCommandExecution() {
        var tools = new BuildTools();
        String result = tools.executeBash("echo hello", "/workspace/GlmCoder");
        assertTrue(result.contains("命令执行成功"));
        assertTrue(result.contains("hello"));
    }

    @Test
    @DisplayName("exec bash blocks dangerous command")
    void dangerousCommandBlocked() {
        var tools = new BuildTools();
        String result = tools.executeBash("rm -rf /tmp/test", "/workspace/GlmCoder");
        assertTrue(result.contains("拒绝执行"));
        assertTrue(result.contains("rm -rf"));
    }

    @Test
    @DisplayName("exec bash reports exit code for failure")
    void errorExitCode(@TempDir Path tmpDir) throws IOException {
        Files.writeString(tmpDir.resolve("test.sh"), "exit 42\n");
        var tools = new BuildTools();
        String result = tools.executeBash("bash test.sh", tmpDir.toString());
        assertTrue(result.contains("exit=42"));
    }

    @Test
    @DisplayName("Output truncated at MAX_OUTPUT_LINES")
    void truncatesLongOutput() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 999; i++) {
            sb.append("line ").append(i).append("\n");
        }
        sb.append("line 999");
        String result = BuildTools.truncateOutput(sb.toString(), 500);
        assertTrue(result.contains("输出被截断"));
        assertTrue(result.contains("1000 行"));
        assertTrue(result.contains("500 行"));
    }

    @Test
    @DisplayName("Output not truncated under limit")
    void noTruncateUnderLimit() {
        String shortOutput = "line1\nline2\nline3\n";
        String result = BuildTools.truncateOutput(shortOutput, 500);
        assertEquals(shortOutput, result);
    }
}
