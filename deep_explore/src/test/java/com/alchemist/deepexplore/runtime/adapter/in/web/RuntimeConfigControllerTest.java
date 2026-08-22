package com.alchemist.deepexplore.runtime.adapter.in.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.runtime.application.RuntimeCapabilitiesService;
import com.alchemist.deepexplore.runtime.application.RuntimeCapabilitiesService.RuntimeCapabilities;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class RuntimeConfigControllerTest {

    @Test
    void exposesModelMetadataWithoutCredentials() {
        RuntimeCapabilitiesService service =
                mock(RuntimeCapabilitiesService.class);
        when(service.current()).thenReturn(new RuntimeCapabilities(
                "DEEPSEEK",
                "deepseek-v4-flash",
                "deepseek-v4-pro",
                true,
                "JINA",
                List.of("JINA", "TAVILY")
        ));
        WebTestClient webTestClient = WebTestClient
                .bindToController(new RuntimeConfigController(service))
                .build();

        webTestClient.get()
                .uri("/api/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.provider").isEqualTo("DEEPSEEK")
                .jsonPath("$.fastModel").isEqualTo("deepseek-v4-flash")
                .jsonPath("$.deepModel").isEqualTo("deepseek-v4-pro")
                .jsonPath("$.defaultSearchProvider").isEqualTo("JINA")
                .jsonPath("$.availableSearchProviders").isArray()
                .jsonPath("$.apiKey").doesNotExist();
    }
}
