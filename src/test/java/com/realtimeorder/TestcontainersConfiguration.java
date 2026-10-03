package com.realtimeorder;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL thật qua Testcontainers cho integration test.
 *
 * {@link ServiceConnection} tự cấu hình datasource của Spring theo container,
 * nên không cần {@code @DynamicPropertySource}.
 * Container là singleton bean: dùng chung cho các test class có cùng Spring context (context caching).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
