package com.example.glmcoder.context;

import com.example.glmcoder.service.ConversationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextCompressorTest {

    @Mock
    ConversationService conversationService;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("L1 truncation keeps short output unchanged")
    void l1ShortOutputUnchanged() {
        var compressor = new ContextCompressor(conversationService);
        String shortResult = "line1\nline2\nline3";
        String result = compressor.l1TruncateToolResult(shortResult);
        assertEquals(shortResult, result);
    }

    @Test
    @DisplayName("L1 truncation cuts output exceeding 500 lines")
    void l1TruncatesLongOutput() {
        var compressor = new ContextCompressor(conversationService);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 999; i++) {
            sb.append("line ").append(i).append("\n");
        }
        sb.append("line 999");
        String result = compressor.l1TruncateToolResult(sb.toString());
        assertTrue(result.contains("L1 截断"));
        assertTrue(result.contains("1000 行"));
    }

    @Test
    @DisplayName("L1 truncation handles empty input")
    void l1HandlesEmpty() {
        var compressor = new ContextCompressor(conversationService);
        assertEquals("", compressor.l1TruncateToolResult(null));
        assertEquals("", compressor.l1TruncateToolResult(""));
        assertEquals("", compressor.l1TruncateToolResult("   "));
    }

    @Test
    @DisplayName("buildFinalPrompt includes truncated tool results")
    void buildFinalPromptIncludesTools() {
        var compressor = new ContextCompressor(conversationService);
        String result = compressor.buildFinalPrompt("sys", "ctx", "query",
                Collections.singletonList("tool output"));
        assertTrue(result.contains("工具调用结果"));
        assertTrue(result.contains("tool output"));
    }

    @Test
    @DisplayName("compress respects max tokens")
    void compressRespectsTokens() {
        var compressor = new ContextCompressor(conversationService);
        String codeSummary = "类:TestClass\n 方法:foo\n 方法:bar\n";
        var result = compressor.compress(codeSummary, Collections.emptyList(), "", 100);
        assertNotNull(result.summary);
        assertTrue(result.estimatedTokens < 100);
    }

    @Test
    @DisplayName("estimateTokens approximates chars/3")
    void estimateTokens() {
        var compressor = new ContextCompressor(conversationService);
        assertEquals(0, compressor.estimateTokens(""));
        assertEquals(1, compressor.estimateTokens("abc"));
        assertEquals(3, compressor.estimateTokens("abcdefghi"));
    }

    @Test
    @DisplayName("L2 check returns false when under threshold")
    void l2UnderThresholdReturnsFalse() {
        when(conversationService.getMessages("conv1")).thenReturn(Collections.emptyList());
        var compressor = new ContextCompressor(conversationService);
        var result = compressor.checkAndCompressConversation("conv1", tempDir);
        assertFalse(result.compressed);
    }

    @Test
    @DisplayName("L2 compression result has correct structure")
    void l2CompressionResultStructure() {
        var result = new ContextCompressor.L2CompressionResult(true, "summary", 10);
        assertTrue(result.compressed);
        assertEquals("summary", result.summary);
        assertEquals(10, result.roundsCompressed);
    }

    @Test
    @DisplayName("CompressedContext fields populated")
    void compressedContextFields() {
        var ctx = new ContextCompressor.CompressedContext();
        ctx.summary = "test";
        ctx.estimatedTokens = 42;
        ctx.keyFiles = java.util.List.of("a.java");
        ctx.keySymbols = java.util.List.of("Foo");

        assertEquals("test", ctx.summary);
        assertEquals(42, ctx.estimatedTokens);
        assertEquals(1, ctx.keyFiles.size());
        assertEquals(1, ctx.keySymbols.size());
    }
}
