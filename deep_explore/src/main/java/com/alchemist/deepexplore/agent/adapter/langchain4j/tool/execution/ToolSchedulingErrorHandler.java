package com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ToolSchedulingErrorHandler implements ToolExecutionErrorHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ToolSchedulingErrorHandler.class);

    private final ObjectMapper objectMapper;

    public ToolSchedulingErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolErrorHandlerResult handle(
            Throwable error,
            ToolErrorContext context
    ) {
        ToolSchedulingException schedulingError = findSchedulingError(error);
        if (schedulingError != null) {
            return ToolErrorHandlerResult.text(jsonFailure(schedulingError));
        }

        String message = errorMessage(error);
        log.warn(
                "Tool '{}' execution failed: {}",
                context.toolExecutionRequest().name(),
                message,
                error
        );
        return ToolErrorHandlerResult.text(message);
    }

    private String jsonFailure(ToolSchedulingException error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("summary", error.getMessage());
        result.put("errorCode", error.code());
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException jsonError) {
            return "{\"ok\":false,\"summary\":\"Tool scheduling failed\","
                    + "\"errorCode\":\"TOOL_SCHEDULING_FAILED\"}";
        }
    }

    private static ToolSchedulingException findSchedulingError(
            Throwable error
    ) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ToolSchedulingException schedulingError) {
                return schedulingError;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String errorMessage(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        Throwable reported = error.getCause() == null
                ? error
                : error.getCause();
        String message = reported.getMessage();
        return message == null || message.isBlank()
                ? reported.getClass().getName()
                : message;
    }
}
