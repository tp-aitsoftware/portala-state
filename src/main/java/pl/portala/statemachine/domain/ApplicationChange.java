package pl.portala.statemachine.domain;

/**
 * Data supplied by the driving adapter together with an {@link ApplicationEvent} .
 *
 * <p>All fields are optional here - which of them are mandatory is decided by the
 * guard of the concrete transition, so that the same command type can serve every
 * event.
 */
public record ApplicationChange(String author, ApplicationReason reason, Long publicationId) {

    public static final ApplicationChange NONE = new ApplicationChange(null, null, null);
}
