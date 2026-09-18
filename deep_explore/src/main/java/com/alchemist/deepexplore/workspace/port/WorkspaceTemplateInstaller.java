package com.alchemist.deepexplore.workspace.port;

import com.alchemist.deepexplore.workspace.domain.StarterTemplate;

public interface WorkspaceTemplateInstaller {

    void initializeIfEmpty(
            String workspaceId,
            StarterTemplate starterTemplate
    );
}
