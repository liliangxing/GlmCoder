package com.example.glmcoder;

import com.example.glmcoder.index.CodeStructureIndex;
import com.example.glmcoder.context.ContextCompressor;
import com.example.glmcoder.security.PathValidator;
import com.example.glmcoder.tools.BuildTools;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GlmCoderIntegrationTests {

    @Autowired
    private CodeStructureIndex codeStructureIndex;

    @Autowired
    private ContextCompressor contextCompressor;

    @Autowired
    private PathValidator pathValidator;

    @Autowired
    private BuildTools buildTools;

    @Test
    void shouldIndexCurrentProject() throws Exception {
        Path projectPath = Path.of("/workspace/GlmCoder");

        codeStructureIndex.buildIndex(
                java.nio.file.Files.walk(projectPath)
                        .filter(p -> p.toString().endsWith(".java"))
                        .toList()
        );

        assertThat(codeStructureIndex.getClassCount()).isGreaterThan(0);
        assertThat(codeStructureIndex.getMethodCount()).isGreaterThan(0);
    }

    @Test
    void shouldValidatePathsCorrectly() {
        Path root = Path.of("/workspace/GlmCoder");

        assertThat(pathValidator.isAllowed(root.resolve("src/main/Test.java"), root)).isTrue();
        assertThat(pathValidator.isAllowed(root.resolve("target/classes/Foo.class"), root)).isFalse();
        assertThat(pathValidator.isAllowed(Path.of("/etc/passwd"), root)).isFalse();
        assertThat(pathValidator.isProtectedFile("pom.xml")).isTrue();
        assertThat(pathValidator.isProtectedFile("MyClass.java")).isFalse();
    }

    @Test
    void shouldCompressContext() {
        String structure = "类: com.example.Foo\n  方法: void doSomething()\n类: com.example.Bar\n  方法: int calculate()\n";
        var result = contextCompressor.compress(structure, List.of("/path/to/file.java"), "", 2000);

        assertThat(result.summary).isNotEmpty();
        assertThat(result.estimatedTokens).isGreaterThan(0);
    }

    @Test
    void shouldEstimateTokens() {
        String text = "Hello World";
        int tokens = contextCompressor.estimateTokens(text);
        assertThat(tokens).isPositive();
    }
}
