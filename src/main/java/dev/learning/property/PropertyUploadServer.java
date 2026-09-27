package dev.learning.property;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PropertyUploadServer {
    private final PropertyPublicationService publications;

    private PropertyUploadServer(PropertyPublicationService publications) { this.publications = publications; }

    public static void main(String[] args) throws IOException {
        ServiceConfig config = ServiceConfig.fromEnvironment();
        PropertyUploadServer application = new PropertyUploadServer(
                new PropertyPublicationService(new InfraiClient(config)));
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/property-submissions", application::handleSubmission);
        server.start();
        System.out.println("Property screening service listening on http://localhost:" + config.port());
    }

    private void handleSubmission(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("error", "Use POST"));
            return;
        }
        try {
            String requestId = header(exchange, "X-Request-Id");
            String caption = header(exchange, "X-Caption");
            PropertyPublicationService.SubmissionKind kind = PropertyPublicationService.parseKind(header(exchange, "X-Submission-Kind"));
            byte[] image = exchange.getRequestBody().readAllBytes();
            var outcome = publications.screen(new PropertyPublicationService.Submission(
                    requestId, kind, caption, header(exchange, "Content-Type"), image));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("requestId", outcome.requestId());
            response.put("decision", outcome.decision().name());
            response.put("submissionKind", kind.name());
            response.put("reason", outcome.reason());
            if (outcome.asset() != null) response.put("asset", Json.parse(outcome.asset()));
            send(exchange, outcome.decision() == PropertyPublicationService.Decision.QUARANTINED ? 202 : 200, response);
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, Map.of("error", exception.getMessage()));
        } catch (InfraiClient.InfraiException exception) {
            int status = exception.status >= 400 && exception.status < 500 ? exception.status : 502;
            send(exchange, status, Map.of("error", exception.code, "message", exception.getMessage()));
        } catch (Exception exception) {
            send(exchange, 502, Map.of("error", "Screening request could not be completed"));
        }
    }

    private static String header(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static void send(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
