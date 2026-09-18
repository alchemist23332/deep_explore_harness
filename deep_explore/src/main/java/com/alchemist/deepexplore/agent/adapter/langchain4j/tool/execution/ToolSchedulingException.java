package com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution;

public class ToolSchedulingException extends RuntimeException {

    private final String code;

    public ToolSchedulingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ToolSchedulingException(
            String code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
