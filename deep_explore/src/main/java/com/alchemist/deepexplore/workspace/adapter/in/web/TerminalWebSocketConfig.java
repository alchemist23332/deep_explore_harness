package com.alchemist.deepexplore.workspace.adapter.in.web;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

@Configuration
public class TerminalWebSocketConfig {

    @Bean
    HandlerMapping terminalWebSocketMapping(
            TerminalWebSocketHandler handler
    ) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        mapping.setUrlMap(Map.of(
                "/api/workspaces/*/terminal",
                handler
        ));
        return mapping;
    }

    @Bean
    WebSocketHandlerAdapter terminalWebSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
