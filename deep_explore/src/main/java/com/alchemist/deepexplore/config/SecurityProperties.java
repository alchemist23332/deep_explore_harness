package com.alchemist.deepexplore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        Mode mode,
        String tenantId,
        String ownerId
) {

    public SecurityProperties {
        mode = mode == null ? Mode.LOCAL : mode;
        tenantId = tenantId == null || tenantId.isBlank()
                ? "local"
                : tenantId;
        ownerId = ownerId == null || ownerId.isBlank()
                ? "local-user"
                : ownerId;
    }

    public enum Mode {
        LOCAL,
        JWT
    }
}
