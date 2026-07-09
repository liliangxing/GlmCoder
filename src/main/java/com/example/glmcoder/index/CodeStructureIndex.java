package com.example.glmcoder.index;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CodeStructureIndex {

    static {
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        StaticJavaParser.setConfiguration(config);
    }

    private final ConcurrentHashMap<String, ClassInfo> classIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MethodInfo> methodIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> fileImports = new ConcurrentHashMap<>();
    private final CallGraphBuilder callGraph = new CallGraphBuilder();

    public void buildIndex(List<Path> javaFiles) {
        classIndex.clear();
        methodIndex.clear();
        fileImports.clear();
        callGraph.clear();

        for (Path file : javaFiles) {
            try {
                String content = Files.readString(file);
                CompilationUnit cu = StaticJavaParser.parse(content);

                String packageName = cu.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString())
                        .orElse("");

                List<String> imports = cu.getImports().stream()
                        .map(i -> i.getNameAsString())
                        .collect(Collectors.toList());
                fileImports.put(file.toString(), imports);

                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                    String className = cls.getNameAsString();
                    String fqn = packageName + "." + className;

                    ClassInfo classInfo = new ClassInfo();
                    classInfo.setName(className);
                    classInfo.setPackageName(packageName);
                    classInfo.setFilePath(file.toString());
                    classInfo.setInterface(cls.isInterface());

                    List<String> methodNames = new ArrayList<>();
                    cls.getMethods().forEach(method -> {
                        String sig = buildMethodSignature(method, className);
                        MethodInfo methodInfo = new MethodInfo();
                        methodInfo.setName(method.getNameAsString());
                        methodInfo.setSignature(sig);
                        methodInfo.setClassName(fqn);
                        methodInfo.setFilePath(file.toString());
                        methodInfo.setReturnType(method.getType().asString());
                        methodInfo.setParameters(method.getParameters().stream()
                                .map(p -> p.getType().asString() + " " + p.getNameAsString())
                                .collect(Collectors.toList()));
                        methodInfo.setBody(method.getBody()
                                .map(b -> b.toString())
                                .orElse(""));
                        methodInfo.setLineNumber(method.getBegin()
                                .map(p -> p.line)
                                .orElse(0));
                        methodIndex.put(fqn + "#" + method.getNameAsString(), methodInfo);
                        methodNames.add(method.getNameAsString());
                    });

                    cls.getFields().forEach(field -> {
                        classInfo.getFields().add(field.getVariables().stream()
                                .map(v -> field.getCommonType().asString() + " " + v.getNameAsString())
                                .collect(Collectors.joining(", ")));
                    });

                    classIndex.put(fqn, classInfo);
                });

            } catch (IOException e) {
                log.warn("Failed to parse: {}", file, e);
            }
        }

        callGraph.build(methodIndex);
        log.info("Indexed {} classes, {} methods", classIndex.size(), methodIndex.size());
    }

    private String buildMethodSignature(MethodDeclaration method, String className) {
        String params = method.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(", "));
        return method.getType().asString() + " " + className + "." + method.getNameAsString() + "(" + params + ")";
    }

    public List<ClassInfo> searchClasses(String keyword) {
        String lower = keyword.toLowerCase();
        return classIndex.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().contains(lower) ||
                             e.getValue().getName().toLowerCase().contains(lower))
                .map(Map.Entry::getValue)
                .limit(20)
                .collect(Collectors.toList());
    }

    public List<MethodInfo> searchMethods(String keyword) {
        String lower = keyword.toLowerCase();
        return methodIndex.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().contains(lower) ||
                             e.getValue().getName().toLowerCase().contains(lower))
                .map(Map.Entry::getValue)
                .limit(20)
                .collect(Collectors.toList());
    }

    public ClassInfo getClassStructure(String className) {
        return classIndex.entrySet().stream()
                .filter(e -> e.getKey().endsWith("." + className) || e.getKey().equals(className))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    public MethodInfo getMethodInfo(String fullMethodRef) {
        return methodIndex.get(fullMethodRef);
    }

    public String getClassSummary() {
        StringBuilder sb = new StringBuilder("=== 项目代码结构总览 ===\n");
        classIndex.forEach((fqn, info) -> {
            sb.append("\n类: ").append(fqn);
            if (info.isInterface()) sb.append(" (接口)");
            sb.append("\n  文件: ").append(info.getFilePath());
            if (!info.getFields().isEmpty()) {
                sb.append("\n  字段: ").append(String.join(", ", info.getFields()));
            }
            List<String> methods = methodIndex.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(fqn + "#"))
                    .map(e -> "    " + e.getValue().getSignature())
                    .collect(Collectors.toList());
            if (!methods.isEmpty()) {
                sb.append("\n  方法:\n").append(String.join("\n", methods));
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    public List<String> getCallers(String fqn, String methodName) {
        return callGraph.getCallers(fqn + "#" + methodName);
    }

    public List<String> getCallees(String fqn, String methodName) {
        return callGraph.getCallees(fqn + "#" + methodName);
    }

    public int getClassCount() { return classIndex.size(); }
    public int getMethodCount() { return methodIndex.size(); }

    @Data
    public static class ClassInfo {
        private String name;
        private String packageName;
        private String filePath;
        private boolean isInterface;
        private List<String> fields = new ArrayList<>();
        private List<String> methods = new ArrayList<>();
    }

    @Data
    public static class MethodInfo {
        private String name;
        private String signature;
        private String className;
        private String filePath;
        private String returnType;
        private List<String> parameters = new ArrayList<>();
        private String body;
        private int lineNumber;
    }
}
