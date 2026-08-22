package com.alchemist.deepexplore.agent.spi;

import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import java.util.List;

public interface WebSearchCapabilities {

    WebSearchProvider defaultProvider();

    List<WebSearchProvider> availableProviders();
}
