package pl.portala.statemachine.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared PostgreSQL Testcontainer for the integration tests. Same image and
 * {@code withReuse(true)} strategy as portala-ecm / portala-ctp, so the local docker
 * cache is reused across builds. {@code @ServiceConnection} wires the container into
 * the datasource, overriding the localhost defaults from application.yml.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DatabaseTestcontainer {

    private static final PostgreSQLContainer DATABASE_CONTAINER = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("portala_state")
            .withReuse(true);

    @Bean
    @ServiceConnection
    PostgreSQLContainer databaseContainer() {
        return DATABASE_CONTAINER;
    }
}
