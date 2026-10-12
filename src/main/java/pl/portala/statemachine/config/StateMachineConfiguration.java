package pl.portala.statemachine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.portala.statemachine.domain.ApplicationStateMachine;

import java.time.Clock;

/**
 * Composition root. The domain classes are deliberately free of Spring
 * annotations , so the state machine and the clock are wired here.
 */
@Configuration
public class StateMachineConfiguration {

    @Bean
    public ApplicationStateMachine applicationStateMachine() {
        return new ApplicationStateMachine();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
