package com.gateready.coding;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** app.judge.mode: disabled (default), local (development and tests only) or judge0 (production). */
@Configuration
class JudgeConfig {
    @Bean
    CodeRunner codeRunner(@Value("${app.judge.mode:disabled}") String mode,
                          @Value("${app.judge.url:}") String url,
                          @Value("${app.judge.token:}") String token,
                          @Value("${app.judge.time-limit-seconds:3}") int seconds) {
        Duration limit = Duration.ofSeconds(seconds);
        return switch (mode) {
            case "local" -> new LocalJavaRunner(limit);
            case "judge0" -> {
                if (url.isBlank()) throw new IllegalStateException("app.judge.url is required when app.judge.mode=judge0");
                yield new Judge0Runner(url, token, limit);
            }
            default -> new DisabledRunner();
        };
    }
}
