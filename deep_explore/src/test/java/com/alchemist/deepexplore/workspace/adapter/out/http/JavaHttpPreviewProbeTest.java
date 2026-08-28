package com.alchemist.deepexplore.workspace.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JavaHttpPreviewProbeTest {

    @Test
    void probesPreviewWithHttp11() throws Exception {
        try (TestServer server = TestServer.respondingWith(200)) {
            boolean healthy = new JavaHttpPreviewProbe().isHealthy(
                    server.url("/health"),
                    Duration.ofSeconds(2)
            );

            assertThat(healthy).isTrue();
            assertThat(server.requestLine())
                    .isEqualTo("GET /health HTTP/1.1");
        }
    }

    @Test
    void treatsServerErrorsAsUnhealthy() throws Exception {
        try (TestServer server = TestServer.respondingWith(500)) {
            boolean healthy = new JavaHttpPreviewProbe().isHealthy(
                    server.url("/"),
                    Duration.ofSeconds(2)
            );

            assertThat(healthy).isFalse();
        }
    }

    private static final class TestServer implements AutoCloseable {

        private final ServerSocket socket;
        private final Thread thread;
        private final AtomicReference<String> requestLine =
                new AtomicReference<>();

        private TestServer(int status) throws Exception {
            socket = new ServerSocket(
                    0,
                    1,
                    InetAddress.getLoopbackAddress()
            );
            thread = Thread.ofVirtual().start(() -> respond(status));
        }

        static TestServer respondingWith(int status) throws Exception {
            return new TestServer(status);
        }

        String url(String path) {
            return "http://127.0.0.1:" + socket.getLocalPort() + path;
        }

        String requestLine() throws InterruptedException {
            thread.join(Duration.ofSeconds(2));
            return requestLine.get();
        }

        private void respond(int status) {
            try (var client = socket.accept();
                    var input = new BufferedReader(new InputStreamReader(
                            client.getInputStream(),
                            StandardCharsets.US_ASCII
                    ))) {
                requestLine.set(input.readLine());
                String line;
                do {
                    line = input.readLine();
                } while (line != null && !line.isEmpty());
                String response = "HTTP/1.1 " + status + " Test\r\n"
                        + "Content-Length: 0\r\n"
                        + "Connection: close\r\n\r\n";
                client.getOutputStream().write(
                        response.getBytes(StandardCharsets.US_ASCII)
                );
            } catch (Exception ignored) {
                // The probe result is the assertion target.
            }
        }

        @Override
        public void close() throws Exception {
            socket.close();
            thread.join(Duration.ofSeconds(2));
        }
    }
}
