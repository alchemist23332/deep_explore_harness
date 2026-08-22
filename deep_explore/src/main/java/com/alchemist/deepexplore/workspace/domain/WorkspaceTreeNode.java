package com.alchemist.deepexplore.workspace.domain;

import java.util.List;

public record WorkspaceTreeNode(
        WorkspaceEntry entry,
        List<WorkspaceTreeNode> children
) {
}
