package com.alchemist.deepexplore.agent.adapter.langchain4j.prompt;

public record PromptFragment(
        String id,
        int order,
        String resourcePath,
        String rootElement
) {

    public PromptFragment {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Prompt fragment id must not be blank");
        }
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException(
                    "Prompt fragment resource path must not be blank"
            );
        }
        if (rootElement == null || rootElement.isBlank()) {
            throw new IllegalArgumentException(
                    "Prompt fragment root element must not be blank"
            );
        }
    }
}
