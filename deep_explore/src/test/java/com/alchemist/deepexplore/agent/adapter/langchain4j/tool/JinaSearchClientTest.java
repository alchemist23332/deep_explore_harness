package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JinaSearchClientTest {

    @Test
    @SuppressWarnings("unchecked")
    void encodesQueryAndReturnsBoundedMarkdown() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("0123456789source");
        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);
        JinaSearchClient client = new JinaSearchClient(
                httpClient,
                "https://s.jina.ai",
                "test-key",
                Duration.ofSeconds(5),
                10
        );

        String result = client.search("DeepSeek V4 release notes");

        ArgumentCaptor<HttpRequest> request =
                ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(
                request.capture(),
                any(HttpResponse.BodyHandler.class)
        );
        assertThat(request.getValue().uri().toString())
                .isEqualTo(
                        "https://s.jina.ai/"
                                + "DeepSeek%20V4%20release%20notes"
                );
        assertThat(request.getValue().headers().firstValue("X-Retain-Images"))
                .contains("none");
        assertThat(request.getValue().headers().firstValue("Authorization"))
                .contains("Bearer test-key");
        assertThat(result)
                .startsWith("0123456789")
                .contains("truncated by Jina result limit");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsNonSuccessfulResponse() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429);
        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);
        JinaSearchClient client = new JinaSearchClient(
                httpClient,
                "https://s.jina.ai/",
                Duration.ofSeconds(5),
                100
        );

        assertThatThrownBy(() -> client.search("query"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Jina Search returned HTTP 429");
    }
}
