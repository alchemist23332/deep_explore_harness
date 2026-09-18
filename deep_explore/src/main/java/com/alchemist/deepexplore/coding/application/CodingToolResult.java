package com.alchemist.deepexplore.coding.application;

public record CodingToolResult(
        boolean ok,
        String summary,
        Object data,
        String error,
        String code
) {

    public static CodingToolResult success(String summary, Object data) {
        return new CodingToolResult(true, summary, data, null, null);
    }

    public static CodingToolResult failure(
            String summary,
            String code
    ) {
        return failure(summary, code, null);
    }

    public static CodingToolResult failure(
            String summary,
            String code,
            Object data
    ) {
        return new CodingToolResult(false, summary, data, summary, code);
    }
}
