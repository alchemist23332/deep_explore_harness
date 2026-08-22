package com.alchemist.deepexplore.agent.adapter.langchain4j.prompt;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Component
public class SystemPromptCatalog {

    private final ResourceLoader resourceLoader;
    private final Map<PromptFragment, String> cache = new ConcurrentHashMap<>();

    public SystemPromptCatalog(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String load(PromptFragment fragment) {
        return cache.computeIfAbsent(fragment, this::readAndValidate);
    }

    public void validateDocument(String content, String expectedRootElement) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Prompt document must not be blank");
        }
        try {
            DocumentBuilderFactory factory = secureDocumentBuilderFactory();
            var document = factory.newDocumentBuilder().parse(
                    new InputSource(new StringReader(content))
            );
            String actualRoot = document.getDocumentElement().getTagName();
            if (!expectedRootElement.equals(actualRoot)) {
                throw new IllegalStateException(
                        "Prompt root element must be <"
                                + expectedRootElement
                                + "> but was <"
                                + actualRoot
                                + ">"
                );
            }
        } catch (ParserConfigurationException | SAXException | IOException error) {
            throw new IllegalStateException(
                    "Prompt is not valid XML: " + error.getMessage(),
                    error
            );
        }
    }

    private String readAndValidate(PromptFragment fragment) {
        Resource resource = resourceLoader.getResource(fragment.resourcePath());
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Prompt resource does not exist: " + fragment.resourcePath()
            );
        }
        try (var input = resource.getInputStream()) {
            String content = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            ).strip();
            validateDocument(content, fragment.rootElement());
            return content;
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Unable to read prompt resource: " + fragment.resourcePath(),
                    error
            );
        }
    }

    private static DocumentBuilderFactory secureDocumentBuilderFactory()
            throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
        );
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false
        );
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }
}
