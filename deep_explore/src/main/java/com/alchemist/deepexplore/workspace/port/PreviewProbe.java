package com.alchemist.deepexplore.workspace.port;

import java.time.Duration;

public interface PreviewProbe {

    boolean isHealthy(String url, Duration timeout);
}
