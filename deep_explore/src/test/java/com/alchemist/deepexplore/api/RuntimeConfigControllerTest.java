package com.alchemist.deepexplore.api;

import com.alchemist.deepexplore.config.AiModelProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class RuntimeConfigControllerTest {

    @Test
    void exposesModelMetadataWithoutApiKey() {
        AiModelProperties properties = new AiModelProperties(
                "DEEPSEEK",
                "sk-must-not-be-exposed",
                "https://api.deepseek.com",
                "deepseek-v4-flash",
                "deepseek-v4-pro",
                0.3,
                1024,
                "",
                4096,
                "high",
                "disabled",
                "enabled",
                Duration.ofSeconds(120),
                false,
                false
        );
        WebTestClient webTestClient = WebTestClient
                .bindToController(new RuntimeConfigController(properties))
                .build();

        webTestClient.get()
                .uri("/api/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.provider").isEqualTo("DEEPSEEK")
                .jsonPath("$.fastModel").isEqualTo("deepseek-v4-flash")
                .jsonPath("$.deepModel").isEqualTo("deepseek-v4-pro")
                .jsonPath("$.apiKey").doesNotExist();
    }
}
