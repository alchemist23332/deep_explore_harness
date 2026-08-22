package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class JinaSearchClient implements WebSearchProviderClient {

    private final HttpClient httpClient;
    private final URI baseUri;
    private final String apiKey;
    private final Duration timeout;
    private final int maxResultCharacters;

    public JinaSearchClient(
            String baseUrl,
            Duration timeout,
            int maxResultCharacters
    ) {
        this(baseUrl, null, timeout, maxResultCharacters);
    }

    public JinaSearchClient(
            String baseUrl,
            String apiKey,
            Duration timeout,
            int maxResultCharacters
    ) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                baseUrl,
                apiKey,
                timeout,
                maxResultCharacters
        );
    }

    JinaSearchClient(
            HttpClient httpClient,
            String baseUrl,
            Duration timeout,
            int maxResultCharacters
    ) {
        this(httpClient, baseUrl, null, timeout, maxResultCharacters);
    }

    JinaSearchClient(
            HttpClient httpClient,
            String baseUrl,
            String apiKey,
            Duration timeout,
            int maxResultCharacters
    ) {
        this.httpClient = httpClient;
        this.baseUri = normalizeBaseUri(baseUrl);
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.maxResultCharacters = maxResultCharacters;
    }

    @Override
    public WebSearchProvider provider() {
        return WebSearchProvider.JINA;
    }

    @Override
    public String search(String query) {
        String encodedQuery = URLEncoder.encode(
                query,
                StandardCharsets.UTF_8
        ).replace("+", "%20");
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        baseUri.resolve(encodedQuery)
                )
                .timeout(timeout)
                .header("Accept", "text/plain")
                .header("X-Retain-Images", "none")
                .GET();
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Jina Search returned HTTP " + response.statusCode()
                );
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("Jina Search returned an empty response");
            }
            return truncate(body);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Jina Search request was interrupted", error);
        } catch (IOException error) {
            throw new IllegalStateException("Jina Search request failed", error);
        }
    }

    private String truncate(String value) {
        if (value.length() <= maxResultCharacters) {
            return value;
        }
        return value.substring(0, maxResultCharacters)
                + "\n\n...[truncated by Jina result limit]";
    }

    private static URI normalizeBaseUri(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        URI uri = URI.create(normalized);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "Jina Search base URL must use HTTP or HTTPS"
            );
        }
        return uri;
    }
}
