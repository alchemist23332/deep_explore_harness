package com.alchemist.deepexplore.runtime.application;

import com.alchemist.deepexplore.agent.spi.WebSearchCapabilities;
import com.alchemist.deepexplore.config.AiModelProperties;
import com.alchemist.deepexplore.config.WebSearchProperties;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class RuntimeCapabilitiesService {

    private final AiModelProperties modelProperties;
    private final WebSearchProperties webSearchProperties;
    private final ObjectProvider<WebSearchCapabilities> webSearchCapabilities;

    public RuntimeCapabilitiesService(
            AiModelProperties modelProperties,
            WebSearchProperties webSearchProperties,
            ObjectProvider<WebSearchCapabilities> webSearchCapabilities
    ) {
        this.modelProperties = modelProperties;
        this.webSearchProperties = webSearchProperties;
        this.webSearchCapabilities = webSearchCapabilities;
    }

    public RuntimeCapabilities current() {
        WebSearchCapabilities search = webSearchCapabilities.getIfAvailable();
        return new RuntimeCapabilities(
                modelProperties.providerType().name(),
                modelProperties.modelName(),
                modelProperties.resolvedDeepModelName(),
                search != null,
                webSearchProperties.defaultProvider().name(),
                search == null
                        ? List.of()
                        : search.availableProviders().stream()
                                .map(Enum::name)
                                .toList()
        );
    }

    public record RuntimeCapabilities(
            String provider,
            String fastModel,
            String deepModel,
            boolean webSearchEnabled,
            String defaultSearchProvider,
            List<String> availableSearchProviders
    ) {
    }
}
