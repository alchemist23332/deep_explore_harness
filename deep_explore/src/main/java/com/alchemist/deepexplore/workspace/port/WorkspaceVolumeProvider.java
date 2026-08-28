package com.alchemist.deepexplore.workspace.port;

import java.nio.file.Path;

public interface WorkspaceVolumeProvider {

    WorkspaceVolume volume(String workspaceId);

    record WorkspaceVolume(
            Path filesDirectory,
            Path mavenCacheDirectory,
            Path npmCacheDirectory,
            Path pnpmCacheDirectory
    ) {
    }
}
