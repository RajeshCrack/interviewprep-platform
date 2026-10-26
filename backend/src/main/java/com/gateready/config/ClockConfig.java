package com.gateready.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    /** The zone decides what "today" means for goals and streaks. */
    @Bean
    Clock clock(AppProperties props) {
        return Clock.system(ZoneId.of(props.zone()));
    }
}
