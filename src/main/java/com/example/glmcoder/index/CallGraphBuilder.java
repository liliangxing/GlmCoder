package com.example.glmcoder.index;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallGraphBuilder {

    private final ConcurrentHashMap<String, List<String>> callers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> callees = new ConcurrentHashMap<>();
    private static final Pattern METHOD_CALL = Pattern.compile("(\\w+)\\s*\\([^)]*\\)");

    public void clear() {
        callers.clear();
        callees.clear();
    }

    public void build(ConcurrentHashMap<String, CodeStructureIndex.MethodInfo> methodIndex) {
        Map<String, String> classNameByMethodName = new HashMap<>();
        for (var entry : methodIndex.entrySet()) {
            var method = entry.getValue();
            classNameByMethodName.put(method.getName(), method.getClassName());
        }

        for (var entry : methodIndex.entrySet()) {
            String callerKey = entry.getKey();
            String body = entry.getValue().getBody();

            Set<String> calledMethods = new HashSet<>();
            Matcher matcher = METHOD_CALL.matcher(body);
            while (matcher.find()) {
                String calledName = matcher.group(1);
                String calledClass = classNameByMethodName.get(calledName);
                if (calledClass != null) {
                    calledMethods.add(calledClass + "#" + calledName);
                }
            }

            callees.put(callerKey, new ArrayList<>(calledMethods));

            for (String called : calledMethods) {
                callers.computeIfAbsent(called, k -> new ArrayList<>()).add(callerKey);
            }
        }
    }

    public List<String> getCallers(String methodKey) {
        return callers.getOrDefault(methodKey, Collections.emptyList());
    }

    public List<String> getCallees(String methodKey) {
        return callees.getOrDefault(methodKey, Collections.emptyList());
    }
}
