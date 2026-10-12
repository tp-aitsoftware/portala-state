package pl.portala.statemachine.domain;

/** F3 - the body / name of a request may only be edited while it is CREATED or VERIFIED. */
public class ContentNotEditableException extends ApplicationException {

    public ContentNotEditableException(ApplicationState state) {
        super("Content can only be edited in state CREATED or VERIFIED, not in %s".formatted(state));
    }
}
