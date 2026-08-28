package com.alchemist.deepexplore.workspace.application.preview;

public record PreviewView(
        PreviewStatus status,
        String url,
        int containerPort,
        Integer hostPort,
        String logs
) {
}
