package pl.portala.statemachine.domain;

/**
 * Lifecycle states of an Application request. 
 */
public enum ApplicationState {
    CREATED,
    VERIFIED,
    ACCEPTED,
    PUBLISHED,
    DELETED;

    public boolean isTerminal() {
        return this == PUBLISHED || this == DELETED;
    }
}
