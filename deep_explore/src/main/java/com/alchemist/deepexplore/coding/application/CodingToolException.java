package com.alchemist.deepexplore.coding.application;

public class CodingToolException extends RuntimeException {

    private final String code;

    public CodingToolException(String code, String message) {
        super(message);
        this.code = code;
    }

    public CodingToolException(
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
