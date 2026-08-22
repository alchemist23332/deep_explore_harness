package com.alchemist.deepexplore.harness.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ToolActivityFormatter {

    private static final int MAX_DETAIL_CHARACTERS = 100;

    private final ObjectMapper objectMapper;

    public ToolActivityFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String displayName(String toolName) {
        return "web_search".equals(toolName) ? "网页搜索" : toolName;
    }

    public String startedSummary(
            String toolName,
            String argumentsJson,
            String provider
    ) {
        if (!"web_search".equals(toolName)) {
            return "正在调用 " + displayName(toolName);
        }
        String providerName = providerDisplayName(provider);
        String query = queryFrom(argumentsJson);
        if (query == null) {
            return "正在使用 " + providerName + " 搜索网页";
        }
        return "正在使用 " + providerName + " 搜索「" + query + "」";
    }

    public String completedSummary(String toolName, boolean success) {
        if ("web_search".equals(toolName)) {
            return success ? "搜索完成，正在整理结果" : "网页搜索失败";
        }
        return displayName(toolName) + (success ? "执行完成" : "执行失败");
    }

    private String queryFrom(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode query = objectMapper.readTree(argumentsJson).get("query");
            if (query == null || !query.isTextual()) {
                return null;
            }
            String normalized = query.asText().strip().replaceAll("\\s+", " ");
            if (normalized.isBlank()) {
                return null;
            }
            return normalized.length() <= MAX_DETAIL_CHARACTERS
                    ? normalized
                    : normalized.substring(0, MAX_DETAIL_CHARACTERS) + "...";
        } catch (RuntimeException | java.io.IOException ignored) {
            return null;
        }
    }

    private static String providerDisplayName(String provider) {
        if ("TAVILY".equalsIgnoreCase(provider)) {
            return "Tavily";
        }
        if ("JINA".equalsIgnoreCase(provider)) {
            return "Jina";
        }
        return "网页搜索服务";
    }
}
