package com.example.glmcoder.index;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {

    private final CodeStructureIndex codeStructureIndex;

    public void indexProject(Path projectPath) throws IOException {
        log.info("Indexing project: {}", projectPath);
        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(projectPath)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                  .filter(p -> !p.toString().contains("/test/"))
                  .filter(p -> !p.toString().contains("/target/"))
                  .forEach(javaFiles::add);
        }
        log.info("Found {} Java files", javaFiles.size());
        codeStructureIndex.buildIndex(javaFiles);
    }

    public CodeStructureIndex getStructureIndex() {
        return codeStructureIndex;
    }
}
