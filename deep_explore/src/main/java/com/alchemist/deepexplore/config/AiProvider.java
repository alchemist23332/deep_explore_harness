package com.alchemist.deepexplore.config;

import java.util.Locale;

public enum AiProvider {
    OPENAI_COMPATIBLE,
    OLLAMA,
    DEEPSEEK;

    public static AiProvider from(String value) {
        if (value == null || value.isBlank()) {
            return OPENAI_COMPATIBLE;
        }
        return AiProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
