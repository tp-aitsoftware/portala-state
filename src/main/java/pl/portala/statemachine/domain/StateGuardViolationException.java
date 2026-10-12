package pl.portala.statemachine.domain;

/**
 * The transition exists in the diagram but one of its guards failed (the AND
 * actions of the stage diagram: missing author, missing reason, reason not
 * cleared, publicationId not assigned, ...). Maps to HTTP 422.
 */
public class StateGuardViolationException extends ApplicationException {

    public StateGuardViolationException(String message) {
        super(message);
    }
}
