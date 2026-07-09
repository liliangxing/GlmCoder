package com.example.glmcoder.security;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
public class PatchApprovalService {

    @Data
    public static class PatchEntry {
        private String filePath;
        private String operation;
        private String originalCode;
        private String modifiedCode;
        private String description;
        private boolean approved;
    }

    private boolean approvalRequired = true;
    private final List<PatchEntry> pendingPatches = new ArrayList<>();

    public void submitPatch(PatchEntry entry) {
        pendingPatches.add(entry);
    }

    public void approve(String filePath) {
        pendingPatches.stream()
                .filter(p -> p.getFilePath().equals(filePath))
                .forEach(p -> p.setApproved(true));
    }

    public void approveAll() {
        pendingPatches.forEach(p -> p.setApproved(true));
    }

    public List<PatchEntry> getPending() {
        return pendingPatches.stream()
                .filter(p -> !p.isApproved())
                .toList();
    }
}
