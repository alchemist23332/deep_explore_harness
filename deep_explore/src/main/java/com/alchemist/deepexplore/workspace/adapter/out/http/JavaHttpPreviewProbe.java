package com.alchemist.deepexplore.workspace.adapter.out.http;

import com.alchemist.deepexplore.workspace.port.PreviewProbe;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class JavaHttpPreviewProbe implements PreviewProbe {

    private final HttpClient client;

    public JavaHttpPreviewProbe() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    JavaHttpPreviewProbe(HttpClient client) {
        this.client = client;
    }

    @Override
    public boolean isHealthy(String url, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .version(HttpClient.Version.HTTP_1_1)
                    .GET()
                    .build();
            int status = client.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            ).statusCode();
            return status >= 200 && status < 500;
        } catch (Exception ignored) {
            return false;
        }
    }
}
