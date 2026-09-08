package com.alchemist.deepexplore.agent.spi;

import com.alchemist.deepexplore.agent.domain.ToolDescriptor;
import java.util.Collection;

public interface ToolDescriptorContributor {

    Collection<ToolDescriptor> toolDescriptors();
}
