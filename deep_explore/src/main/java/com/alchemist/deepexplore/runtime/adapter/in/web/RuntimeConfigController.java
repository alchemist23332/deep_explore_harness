package com.alchemist.deepexplore.runtime.adapter.in.web;

import com.alchemist.deepexplore.runtime.application.RuntimeCapabilitiesService;
import com.alchemist.deepexplore.runtime.application.RuntimeCapabilitiesService.RuntimeCapabilities;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class RuntimeConfigController {

    private final RuntimeCapabilitiesService runtimeCapabilities;

    public RuntimeConfigController(RuntimeCapabilitiesService runtimeCapabilities) {
        this.runtimeCapabilities = runtimeCapabilities;
    }

    @GetMapping
    public RuntimeCapabilities getConfig() {
        return runtimeCapabilities.current();
    }
}
