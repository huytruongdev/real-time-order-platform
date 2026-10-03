package com.realtimeorder.shared.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Inject {@link Clock} thay vì gọi {@code Instant.now()} trực tiếp,
 * để test có thể điều khiển thời gian (token expiration, payment expiration, ...).
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
