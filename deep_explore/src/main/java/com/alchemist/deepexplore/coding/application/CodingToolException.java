package com.alchemist.deepexplore.coding.application;

public class CodingToolException extends RuntimeException {

    private final String code;
    private final Object data;

    public CodingToolException(String code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    public CodingToolException(
            String code,
            String message,
            Object data
    ) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public CodingToolException(
            String code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.data = null;
    }

    public String code() {
        return code;
    }

    public Object data() {
        return data;
    }
}
