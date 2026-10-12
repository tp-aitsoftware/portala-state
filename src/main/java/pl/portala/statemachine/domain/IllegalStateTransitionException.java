package pl.portala.statemachine.domain;

/**
 * The event is not permitted from the current state or any event on a terminal PUBLISHED / DELETED request.
 * Maps to HTTP 409 at the driving adapter.
 */
public class IllegalStateTransitionException extends ApplicationException {

    public IllegalStateTransitionException(ApplicationState from, ApplicationEvent event) {
        super("Event %s is not allowed from state %s".formatted(event, from));
    }
}
