package com.alchemist.deepexplore.harness.application.query;

import com.alchemist.deepexplore.agent.application.ToolDescriptorRegistry;
import com.alchemist.deepexplore.agent.domain.ToolDescriptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ToolActivityFormatter {

    private static final int MAX_DETAIL_CHARACTERS = 100;

    private final ObjectMapper objectMapper;
    private final ToolDescriptorRegistry descriptors;

    public ToolActivityFormatter(
            ObjectMapper objectMapper,
            ToolDescriptorRegistry descriptors
    ) {
        this.objectMapper = objectMapper;
        this.descriptors = descriptors;
    }

    public String displayName(String toolName) {
        return descriptors.descriptor(toolName).displayName();
    }

    public String startedSummary(
            String toolName,
            String argumentsJson,
            String provider
    ) {
        ToolDescriptor descriptor = descriptors.descriptor(toolName);
        if (descriptor.argumentExposure()
                == ToolDescriptor.ArgumentExposure.DESCRIPTION) {
            String description = textField(argumentsJson, "description");
            return description == null
                    ? "正在调用 " + descriptor.displayName()
                    : description;
        }
        if (descriptor.argumentExposure()
                == ToolDescriptor.ArgumentExposure.QUERY) {
            String query = queryFrom(argumentsJson);
            String prefix = "正在使用 " + providerDisplayName(provider)
                    + " 调用" + descriptor.displayName();
            return query == null ? prefix : prefix + "「" + query + "」";
        }
        return "正在调用 " + descriptor.displayName();
    }

    public String completedSummary(
            String toolName,
            String result,
            boolean success
    ) {
        ToolDescriptor descriptor = descriptors.descriptor(toolName);
        if (descriptor.resultExposure()
                == ToolDescriptor.ResultExposure.SUMMARY) {
            String summary = textField(result, "summary");
            if (summary != null) {
                return summary;
            }
        }
        return descriptor.displayName() + (success ? "执行完成" : "执行失败");
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
