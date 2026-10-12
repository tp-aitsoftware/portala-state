package pl.portala.statemachine.domain;

import java.util.Arrays;

/** The event name sent by the client is not one of {@link ApplicationEvent} (HTTP 400). */
public class UnknownApplicationEventException extends ApplicationException {

    public UnknownApplicationEventException(String eventName) {
        super("Unknown event '%s'; legal events are %s".formatted(eventName, Arrays.toString(ApplicationEvent.values())));
    }
}
