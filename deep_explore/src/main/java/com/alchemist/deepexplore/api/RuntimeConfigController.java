package com.alchemist.deepexplore.api;

import com.alchemist.deepexplore.config.AiModelProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class RuntimeConfigController {

    private final AiModelProperties properties;

    public RuntimeConfigController(AiModelProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public RuntimeConfigResponse getConfig() {
        return new RuntimeConfigResponse(
                properties.providerType().name(),
                properties.modelName(),
                properties.resolvedDeepModelName()
        );
    }

    public record RuntimeConfigResponse(
            String provider,
            String fastModel,
            String deepModel
    ) {
    }
}
