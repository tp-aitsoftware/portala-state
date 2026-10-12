package pl.portala.statemachine.domain;

/** Base type for domain failures raised while firing an {@link ApplicationEvent}. */
public abstract class ApplicationException extends RuntimeException {

    protected ApplicationException(String message) {
        super(message);
    }
}
