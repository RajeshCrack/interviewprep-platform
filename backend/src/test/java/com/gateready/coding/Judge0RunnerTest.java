package com.gateready.coding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Checks the request we send to Judge0 and how each Judge0 status maps to our statuses, against a fake server. */
class Judge0RunnerTest {
    private HttpServer server;
    private final AtomicReference<String> reply = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> tokenHeader = new AtomicReference<>();
    private int httpStatus = 200;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            path.set(ex.getRequestURI().toString());
            tokenHeader.set(ex.getRequestHeaders().getFirst("X-Auth-Token"));
            byte[] out = reply.get().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(httpStatus, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    private Judge0Runner runner() {
        return new Judge0Runner("http://127.0.0.1:" + server.getAddress().getPort() + "/", "secret", Duration.ofSeconds(2));
    }

    @Test
    void sendsJavaSourceStdinLimitsAndTheTokenToJudge0() throws Exception {
        reply.set("{\"status\":{\"id\":3},\"stdout\":\"6\\n\"}");
        CodeRunner.Outcome o = runner().run("class Main {}", "1 2 3");
        JsonNode sent = new ObjectMapper().readTree(body.get());
        assertThat(sent.get("language_id").asInt()).isEqualTo(62);
        assertThat(sent.get("source_code").asText()).isEqualTo("class Main {}");
        assertThat(sent.get("stdin").asText()).isEqualTo("1 2 3");
        assertThat(sent.get("cpu_time_limit").asDouble()).isEqualTo(2.0);
        assertThat(path.get()).isEqualTo("/submissions?base64_encoded=false&wait=true");
        assertThat(tokenHeader.get()).isEqualTo("secret");
        assertThat(o.status()).isEqualTo(CodeRunner.Status.OK);
        assertThat(o.stdout()).isEqualTo("6\n");
    }

    @Test
    void mapsJudge0StatusesToOurs() {
        record Case(int id, CodeRunner.Status expected) {}
        for (Case c : new Case[]{new Case(3, CodeRunner.Status.OK), new Case(5, CodeRunner.Status.TIME_LIMIT),
                new Case(6, CodeRunner.Status.COMPILE_ERROR), new Case(11, CodeRunner.Status.RUNTIME_ERROR),
                new Case(13, CodeRunner.Status.UNAVAILABLE), new Case(99, CodeRunner.Status.UNAVAILABLE)}) {
            reply.set("{\"status\":{\"id\":" + c.id() + ",\"description\":\"d\"},\"stdout\":null,\"stderr\":\"boom\",\"compile_output\":\"bad code\"}");
            assertThat(runner().run("x", "").status()).as("status id " + c.id()).isEqualTo(c.expected());
        }
    }

    @Test
    void compileErrorCarriesTheCompilerMessage() {
        reply.set("{\"status\":{\"id\":6},\"compile_output\":\"Main.java:3: error: ';' expected\"}");
        CodeRunner.Outcome o = runner().run("x", "");
        assertThat(o.stderr()).contains("';' expected");
    }

    @Test
    void aServerErrorOrAnUnreachableJudgeIsUnavailableNotAWrongAnswer() {
        httpStatus = 500;
        reply.set("oops");
        assertThat(runner().run("x", "").status()).isEqualTo(CodeRunner.Status.UNAVAILABLE);
        server.stop(0);
        assertThat(runner().run("x", "").status()).isEqualTo(CodeRunner.Status.UNAVAILABLE);
    }
}
