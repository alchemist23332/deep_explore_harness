package com.alchemist.deepexplore.workspace.adapter.out.docker;

import com.alchemist.deepexplore.workspace.application.WorkspaceProperties;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class DockerClientManager {

    private final DockerClient docker;

    public DockerClientManager(WorkspaceProperties properties) {
        DockerClientConfig config = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(URI.create(properties.docker().host()))
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(3))
                .responseTimeout(Duration.ZERO)
                .build();
        this.docker = DockerClientImpl.getInstance(config, httpClient);
    }

    public DockerClient client() {
        return docker;
    }

    @PreDestroy
    void close() {
        try {
            docker.close();
        } catch (IOException ignored) {
            // The process is already shutting down.
        }
    }
}
