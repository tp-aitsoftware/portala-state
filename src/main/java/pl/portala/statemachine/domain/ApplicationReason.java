package pl.portala.statemachine.domain;

/**
 * Reason recorded whenever a request reaches the DELETED state, either via
 * DELETE (from CREATED) or via REJECT (from VERIFIED / ACCEPTED).
 */
public enum ApplicationReason {
    /** Set when the request is deleted from CREATED via the DELETE event. */
    FINISH_ON_DELETE,
    /** Set when the request is rejected from VERIFIED / ACCEPTED via the REJECT event. */
    FINISH_ON_REJECTED
}
