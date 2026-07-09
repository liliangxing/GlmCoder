package com.example.glmcoder.attachment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class AttachmentManager {

    private final ConcurrentMap<String, String> attachments = new ConcurrentHashMap<>();

    public String storeAttachment(MultipartFile file, Path workspaceDir) throws IOException {
        Path target = workspaceDir.resolve(file.getOriginalFilename());
        Files.write(target, file.getBytes());
        String content = Files.readString(target);
        attachments.put(target.toString(), content);
        log.info("Stored attachment: {}", file.getOriginalFilename());
        return target.toString();
    }

    public String readAttachment(String filePath) throws IOException {
        if (attachments.containsKey(filePath)) {
            return attachments.get(filePath);
        }
        String content = Files.readString(Path.of(filePath));
        attachments.put(filePath, content);
        return content;
    }

    public String summarizeFile(String filePath) throws IOException {
        String content = readAttachment(filePath);
        String[] lines = content.split("\n");
        StringBuilder summary = new StringBuilder();
        summary.append("文件: ").append(filePath).append("\n");
        summary.append("行数: ").append(lines.length).append("\n");
        summary.append("大小: ").append(content.length()).append(" 字符\n");
        return summary.toString();
    }
}
