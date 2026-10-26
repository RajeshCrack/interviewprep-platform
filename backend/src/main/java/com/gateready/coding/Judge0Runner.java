package com.gateready.coding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runs code on a Judge0 server, which sandboxes it. Java is language id 62. */
public class Judge0Runner implements CodeRunner {
    private static final int JAVA = 62;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private final String token;
    private final double timeLimitSeconds;

    public Judge0Runner(String baseUrl, String token, Duration timeLimit) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        this.timeLimitSeconds = timeLimit.toMillis() / 1000.0;
    }

    @Override
    public Outcome run(String sourceCode, String stdin) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("source_code", sourceCode);
            body.put("language_id", JAVA);
            body.put("stdin", stdin == null ? "" : stdin);
            body.put("cpu_time_limit", timeLimitSeconds);
            body.put("wall_time_limit", timeLimitSeconds * 3);
            body.put("memory_limit", 262144);
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + "/submissions?base64_encoded=false&wait=true"))
                    .timeout(Duration.ofSeconds(60)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (token != null && !token.isBlank()) req.header("X-Auth-Token", token);
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) return Outcome.of(Status.UNAVAILABLE, "", "Judge answered " + res.statusCode());
            return map(json.readTree(res.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.of(Status.UNAVAILABLE, "", "Interrupted");
        } catch (Exception e) {
            return Outcome.of(Status.UNAVAILABLE, "", "Judge unreachable: " + e.getMessage());
        }
    }

    static Outcome map(JsonNode r) {
        String stdout = text(r, "stdout");
        String stderr = text(r, "stderr");
        int id = r.path("status").path("id").asInt(-1);
        return switch (id) {
            case 3 -> Outcome.of(Status.OK, stdout, stderr);
            case 5 -> Outcome.of(Status.TIME_LIMIT, stdout, "");
            case 6 -> Outcome.of(Status.COMPILE_ERROR, "", text(r, "compile_output"));
            case 7, 8, 9, 10, 11, 12 -> Outcome.of(Status.RUNTIME_ERROR, stdout, stderr.isBlank() ? text(r.path("status"), "description") : stderr);
            default -> Outcome.of(Status.UNAVAILABLE, "", "Judge status " + id);
        };
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }
}
