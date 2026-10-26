package com.gateready.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String zone, String corsOrigins, Jwt jwt) {
    public record Jwt(String secret, int accessMinutes, int refreshDays) {}
}
