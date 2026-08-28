package com.alchemist.deepexplore.workspace.application.query;

import com.alchemist.deepexplore.workspace.config.WorkspaceProperties;
import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.port.SandboxRuntime;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RuntimeProfileQueryService {

    private final SandboxRuntime runtime;
    private final WorkspaceProperties properties;

    public RuntimeProfileQueryService(
            SandboxRuntime runtime,
            WorkspaceProperties properties
    ) {
        this.runtime = runtime;
        this.properties = properties;
    }

    public RuntimeOverview overview() {
        SandboxRuntime.Availability availability = runtime.availability();
        List<RuntimeProfileView> profiles = Arrays.stream(
                        RuntimeProfile.values()
                )
                .map(profile -> new RuntimeProfileView(
                        profile.name(),
                        profile.displayName(),
                        profile.description()
                ))
                .toList();
        return new RuntimeOverview(
                properties.enabled(),
                availability.available(),
                availability.message(),
                profiles
        );
    }

    public record RuntimeOverview(
            boolean enabled,
            boolean available,
            String message,
            List<RuntimeProfileView> profiles
    ) {
    }

    public record RuntimeProfileView(
            String id,
            String displayName,
            String description
    ) {
    }
}
