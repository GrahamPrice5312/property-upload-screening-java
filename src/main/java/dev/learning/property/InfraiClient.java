package dev.learning.property;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

final class InfraiClient implements InfraiGateway {
    private final HttpClient http;
    private final ServiceConfig config;

    InfraiClient(ServiceConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public ModerationResult moderate(byte[] image, String contentType, String caption) throws Exception {
        String imageUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(image);
        Map<String, Object> body = Map.of(
                "model", "omni-moderation-latest",
                "input", List.of(
                        Map.of("type", "text", "text", caption),
                        Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))));
        Map<String, Object> data = sendEnvelope("POST", "/v1/moderations", "application/json",
                Json.stringify(body).getBytes(StandardCharsets.UTF_8), null);
        return new ModerationResult(readFlagged(data));
    }

    @Override
    public String resize(byte[] image, String contentType, String requestId) throws Exception {
        String boundary = "infrai-" + requestId.replaceAll("[^A-Za-z0-9]", "");
        byte[] body = resizeBody(boundary, image, contentType);
        Map<String, Object> data = sendEnvelope("POST", "/v1/image/resize",
                "multipart/form-data; boundary=" + boundary, body, requestId);
        return Json.stringify(data);
    }

    private Map<String, Object> sendEnvelope(String method, String path, String contentType, byte[] body,
                                              String idempotencyKey) throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", contentType)
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope = object(Json.parse(response.body()));
            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                throw InfraiException.from(response.statusCode(), object(envelope.get("error")));
            }
            if (response.statusCode() >= 500) {
                throw new InfraiException(response.statusCode(), "TRANSPORT_ERROR", "Upstream request failed");
            }
            return object(envelope.get("data"));
        }
        throw new IllegalStateException("Retry loop exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try { return Math.max(1L, Long.parseLong(value)) * 1000L; }
                    catch (NumberFormatException ignored) { return 500L * (1L << attempt); }
                })
                .orElse(500L * (1L << attempt));
    }

    private static byte[] resizeBody(String boundary, byte[] image, String contentType) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        field(out, boundary, "width", "1600");
        field(out, boundary, "height", "1200");
        field(out, boundary, "fit", "inside");
        field(out, boundary, "enlarge", "false");
        field(out, boundary, "format", "webp");
        field(out, boundary, "store", "false");
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"property-upload\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(image);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static boolean readFlagged(Map<String, Object> data) {
        Object results = data.get("results");
        if (!(results instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Moderation response has no results");
        }
        return Boolean.TRUE.equals(object(list.get(0)).get("flagged"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected JSON object");
        return (Map<String, Object>) value;
    }

    static final class InfraiException extends Exception {
        final int status;
        final String code;

        InfraiException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        static InfraiException from(int status, Map<String, Object> error) {
            return new InfraiException(status, String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED")),
                    String.valueOf(error.getOrDefault("message", "Request rejected")));
        }
    }
}
