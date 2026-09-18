package com.alchemist.deepexplore.workspace.adapter.out.filesystem;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.domain.StarterTemplate;
import com.alchemist.deepexplore.workspace.port.WorkspaceTemplateInstaller;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class ClasspathWorkspaceTemplateInstaller implements WorkspaceTemplateInstaller {

    private static final String JAVA_21_TEMPLATE =
            "classpath:workspace-templates/java21/";
    private static final String WEB_TYPESCRIPT_TEMPLATE =
            "classpath:workspace-templates/web-typescript/";
    private static final String EMPTY_TEMPLATE =
            "classpath:workspace-templates/empty/";

    private final LocalWorkspaceLayout directories;
    private final ResourceLoader resources;

    public ClasspathWorkspaceTemplateInstaller(
            LocalWorkspaceLayout directories,
            ResourceLoader resources
    ) {
        this.directories = directories;
        this.resources = resources;
    }

    @Override
    public void initializeIfEmpty(
            String workspaceId,
            StarterTemplate starterTemplate
    ) {
        directories.initialize(workspaceId);
        Path root = directories.filesDirectory(workspaceId);
        if (!isEmpty(root)) {
            return;
        }
        switch (starterTemplate) {
            case WEB_TYPESCRIPT ->
                    copyTemplate(workspaceId, WEB_TYPESCRIPT_TEMPLATE);
            case JAVA_MAVEN ->
                    copyTemplate(workspaceId, JAVA_21_TEMPLATE);
            case EMPTY -> copyTemplate(workspaceId, EMPTY_TEMPLATE);
        }
    }

    private void copyTemplate(String workspaceId, String templateRoot) {
        for (String relativePath : readManifest(templateRoot)) {
            Path target = directories.resolveFile(workspaceId, relativePath);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            Resource source = resources.getResource(
                    templateRoot + relativePath
            );
            if (!source.exists()) {
                throw templateError(
                        "Workspace template resource is missing: "
                                + relativePath,
                        null
                );
            }
            try {
                Files.createDirectories(target.getParent());
                directories.makeContainerWritable(target.getParent(), true);
                try (var input = source.getInputStream()) {
                    Files.copy(input, target);
                }
                directories.makeContainerWritable(target, false);
            } catch (IOException error) {
                throw templateError(
                        "Unable to initialize workspace template",
                        error
                );
            }
        }
    }

    private List<String> readManifest(String templateRoot) {
        Resource manifest = resources.getResource(
                templateRoot + "manifest.txt"
        );
        try (
                var reader = new BufferedReader(new InputStreamReader(
                        manifest.getInputStream(),
                        StandardCharsets.UTF_8
                ))
        ) {
            return reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .toList();
        } catch (IOException error) {
            throw templateError(
                    "Unable to read workspace template manifest",
                    error
            );
        }
    }

    private static boolean isEmpty(Path directory) {
        try (var entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        } catch (IOException error) {
            throw templateError(
                    "Unable to inspect workspace template directory",
                    error
            );
        }
    }

    private static WorkspaceOperationException templateError(
            String message,
            Exception error
    ) {
        return new WorkspaceOperationException(
                "WORKSPACE_TEMPLATE_FAILED",
                message,
                error
        );
    }
}
