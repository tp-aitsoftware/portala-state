package pl.portala.statemachine.domain;

import java.util.Arrays;

/** The state filter sent by the client is not one of {@link ApplicationState} (HTTP 400). */
public class UnknownApplicationStateException extends ApplicationException {

    public UnknownApplicationStateException(String stateName) {
        super("Unknown state '%s'; legal states are %s".formatted(stateName, Arrays.toString(ApplicationState.values())));
    }
}
