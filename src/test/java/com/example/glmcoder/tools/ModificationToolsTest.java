package com.example.glmcoder.tools;

import com.example.glmcoder.project.ProjectManager;
import com.example.glmcoder.security.PathValidator;
import com.example.glmcoder.security.PatchApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModificationToolsTest {

    @Mock
    ProjectManager projectManager;
    @Mock
    PathValidator pathValidator;
    @Mock
    PatchApprovalService patchApprovalService;

    @TempDir
    Path tempDir;

    private ModificationTools createTools() {
        when(projectManager.getProjectPath("test")).thenReturn(tempDir);
        when(pathValidator.isAllowed(any(), any())).thenReturn(true);
        when(pathValidator.isProtectedFile(any())).thenReturn(false);
        return new ModificationTools(projectManager, pathValidator, patchApprovalService);
    }

    @Test
    @DisplayName("Unique match replaces correctly")
    void uniqueMatchReplaced() throws IOException {
        Path file = tempDir.resolve("Unique.java");
        Files.writeString(file, "public class Foo {\n    private String name;\n}\n");

        var tools = createTools();
        String result = tools.editFile("Unique.java", "private String name;", "private String fullName;", "test");

        assertTrue(result.contains("成功"));
        String updated = Files.readString(file);
        assertTrue(updated.contains("private String fullName;"));
    }

    @Test
    @DisplayName("Non-unique match returns error with positions")
    void nonUniqueMatchReturnsPositions() throws IOException {
        Path file = tempDir.resolve("Dupe.java");
        Files.writeString(file, "int x = 1;\nint y = 2;\nint x = 3;\n");

        var tools = createTools();
        String result = tools.editFile("Dupe.java", "int x =", "float x =", "test");

        assertTrue(result.contains("不唯一"));
        assertTrue(result.contains("位置 1"));
        assertTrue(result.contains("位置 2"));
    }

    @Test
    @DisplayName("No match returns error")
    void noMatchReturnsError() throws IOException {
        Path file = tempDir.resolve("None.java");
        Files.writeString(file, "class A {\n    void foo() {}\n}\n");

        var tools = createTools();
        String result = tools.editFile("None.java", "void bar() {}", "void baz() {}", "test");

        assertTrue(result.contains("未找到 oldText"));
    }

    @Test
    @DisplayName("Whitespace variant rejected with normalization suggestion")
    void whitespaceVariantSuggestsNormalized() throws IOException {
        Path file = tempDir.resolve("Space.java");
        Files.writeString(file, "hello world\nline two\n");
        String variant = "hello\tworld\nline two";

        var tools = createTools();
        String result = tools.editFile("Space.java", variant, "xxx", "test");

        assertTrue(result.contains("规范化"));
    }

    @Test
    @DisplayName("File not found via invalid path returns error")
    void fileNotFoundGivesError() {
        when(projectManager.getProjectPath("test")).thenReturn(tempDir);
        when(pathValidator.isAllowed(any(), any())).thenReturn(false);

        var tools = new ModificationTools(projectManager, pathValidator, patchApprovalService);
        String result = tools.editFile("nonexistent", "old", "new", "test");
        assertTrue(result.contains("不允许"));
    }

    @Test
    @DisplayName("normalizeWhitespace cleans whitespace and line endings")
    void normalizeWhitespaceCleans() {
        String input = "\tpublic class\r\n    void test()\r\n";
        String result = ModificationTools.normalizeWhitespace(input);
        assertFalse(result.contains("\t"));
        assertFalse(result.contains("\r"));
        assertEquals("public class\nvoid test()", result);
    }

    @Test
    @DisplayName("countLinesBeforeIndex correct for multiline")
    void countLinesBeforeIndex() {
        String content = "line1\nline2\nline3\nline4\n";
        assertEquals(1, ModificationTools.countLinesBeforeIndex(content, 0));
        assertEquals(2, ModificationTools.countLinesBeforeIndex(content, 6));
        assertEquals(4, ModificationTools.countLinesBeforeIndex(content, 18));
    }

    @Test
    @DisplayName("checkUniqueMatch returns index for unique match")
    void checkUniqueMatchIndex() {
        String content = "aaa\nbbb\nccc\n";
        var result = ModificationTools.checkUniqueMatch(content, "bbb");
        assertTrue(result.matched);
        assertTrue(result.matchIndex >= 0);
    }

    @Test
    @DisplayName("checkUniqueMatch for non-unique match")
    void checkUniqueMatchNonUnique() {
        String content = "a x b\na y b\na x b\n";
        var result = ModificationTools.checkUniqueMatch(content, "a x");
        assertFalse(result.matched);
        assertTrue(result.message.contains("不唯一"));
    }

    @Test
    @DisplayName("checkUniqueMatch for no match")
    void checkUniqueMatchNoMatch() {
        var result = ModificationTools.checkUniqueMatch("abc", "xyz");
        assertFalse(result.matched);
    }
}
