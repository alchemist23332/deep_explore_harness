package com.alchemist.deepexplore.workspace.application;

import com.alchemist.deepexplore.workspace.domain.StarterTemplate;
import com.alchemist.deepexplore.workspace.port.WorkspaceTemplateInstaller;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceTemplateInitializer {

    private final WorkspaceTemplateInstaller installer;

    public WorkspaceTemplateInitializer(
            WorkspaceTemplateInstaller installer
    ) {
        this.installer = installer;
    }

    public void initializeIfEmpty(
            String workspaceId,
            StarterTemplate starterTemplate
    ) {
        installer.initializeIfEmpty(workspaceId, starterTemplate);
    }
}
