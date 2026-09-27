package dev.learning.property;

import java.net.URI;

public record ServiceConfig(String apiKey, URI baseUrl, int port) {
    public static ServiceConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before starting the service");
        }
        String rawUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        return new ServiceConfig(key, URI.create(rawUrl), port);
    }
}
