package pl.portala.statemachine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The state machine managing the lifecycle of Application requests
 * for the future ecommerce domain. Backend-only REST service, JDBC
 * persistence on PostgreSQL, append-only audit history.
 */
@SpringBootApplication
public class StateMachineApplication {

    public static void main(String[] args) {
        SpringApplication.run(StateMachineApplication.class, args);
    }
}
