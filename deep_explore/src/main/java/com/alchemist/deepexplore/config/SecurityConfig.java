package com.alchemist.deepexplore.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(
            prefix = "app.security",
            name = "mode",
            havingValue = "LOCAL",
            matchIfMissing = true
    )
    SecurityWebFilterChain localSecurity(ServerHttpSecurity http) {
        return base(http)
                .authorizeExchange(exchange ->
                        exchange.anyExchange().permitAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.security",
            name = "mode",
            havingValue = "JWT"
    )
    SecurityWebFilterChain jwtSecurity(
            ServerHttpSecurity http,
            SecurityProperties properties
    ) {
        return base(http)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/health", "/api/config").permitAll()
                        .anyExchange().access((authentication, context) ->
                                authentication.map(value ->
                                        new AuthorizationDecision(
                                                value.isAuthenticated()
                                                        && properties.ownerId()
                                                        .equals(value.getName())
                                        ))))
                .oauth2ResourceServer(resourceServer ->
                        resourceServer.jwt(jwt -> {
                        }))
                .build();
    }

    private static ServerHttpSecurity base(ServerHttpSecurity http) {
        return http
                .cors(Customizer.withDefaults())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable);
    }
}
