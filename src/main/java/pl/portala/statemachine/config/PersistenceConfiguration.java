package pl.portala.statemachine.config;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/** Provides the {@link JdbcClient} used by the JDBC driven adapters. */
@Configuration
public class PersistenceConfiguration {

    @Bean
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }
}
