package com.alchemist.deepexplore.harness.application.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ToolActivityFormatter {

    private static final int MAX_DETAIL_CHARACTERS = 100;

    private final ObjectMapper objectMapper;

    public ToolActivityFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String displayName(String toolName) {
        return Map.ofEntries(
                Map.entry("web_search", "网页搜索"),
                Map.entry("list_files", "浏览文件"),
                Map.entry("read_file", "读取文件"),
                Map.entry("grep_search", "搜索代码"),
                Map.entry("write_file", "写入文件"),
                Map.entry("apply_patch", "应用补丁"),
                Map.entry("run_command", "运行命令"),
                Map.entry("start_preview", "启动预览"),
                Map.entry("preview_status", "检查预览"),
                Map.entry("preview_logs", "读取预览日志"),
                Map.entry("stop_preview", "停止预览")
        ).getOrDefault(toolName, toolName);
    }

    public String startedSummary(
            String toolName,
            String argumentsJson,
            String provider
    ) {
        if (!"web_search".equals(toolName)) {
            String description = textField(argumentsJson, "description");
            if (description != null) {
                return description;
            }
            return "正在调用 " + displayName(toolName);
        }
        String providerName = providerDisplayName(provider);
        String query = queryFrom(argumentsJson);
        if (query == null) {
            return "正在使用 " + providerName + " 搜索网页";
        }
        return "正在使用 " + providerName + " 搜索「" + query + "」";
    }

    public String completedSummary(
            String toolName,
            String result,
            boolean success
    ) {
        if ("web_search".equals(toolName)) {
            return success ? "搜索完成，正在整理结果" : "网页搜索失败";
        }
        String summary = textField(result, "summary");
        if (summary != null) {
            return summary;
        }
        return displayName(toolName) + (success ? "执行完成" : "执行失败");
    }

    public String completedSummary(String toolName, boolean success) {
        return completedSummary(toolName, null, success);
    }

    private String queryFrom(String argumentsJson) {
        return textField(argumentsJson, "query");
    }

    private String textField(String json, String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode value = objectMapper.readTree(json).get(field);
            if (value == null || !value.isTextual()) {
                return null;
            }
            String normalized = value.asText().strip().replaceAll("\\s+", " ");
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
