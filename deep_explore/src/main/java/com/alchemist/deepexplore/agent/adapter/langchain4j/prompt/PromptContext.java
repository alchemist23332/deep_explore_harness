package com.alchemist.deepexplore.agent.adapter.langchain4j.prompt;

public record PromptContext(boolean codingWorkspace) {

    public static final PromptContext DEFAULT = new PromptContext(false);
}
