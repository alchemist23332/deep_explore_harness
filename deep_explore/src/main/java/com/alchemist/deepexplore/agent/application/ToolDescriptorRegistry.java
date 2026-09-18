package com.alchemist.deepexplore.agent.application;

import com.alchemist.deepexplore.agent.domain.ToolDescriptor;
import com.alchemist.deepexplore.agent.spi.ToolDescriptorContributor;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ToolDescriptorRegistry {

    private final Map<String, ToolDescriptor> descriptors;

    public ToolDescriptorRegistry(
            List<ToolDescriptorContributor> contributors
    ) {
        this.descriptors = contributors.stream()
                .flatMap(contributor ->
                        contributor.toolDescriptors().stream())
                .collect(Collectors.toUnmodifiableMap(
                        ToolDescriptor::name,
                        Function.identity()
                ));
    }

    public ToolDescriptor descriptor(String toolName) {
        return descriptors.getOrDefault(
                toolName,
                new ToolDescriptor(
                        toolName,
                        toolName,
                        ToolDescriptor.ArgumentExposure.NONE,
                        ToolDescriptor.ResultExposure.NONE
                )
        );
    }

    public List<ToolDescriptor> list() {
        return List.copyOf(descriptors.values());
    }
}
