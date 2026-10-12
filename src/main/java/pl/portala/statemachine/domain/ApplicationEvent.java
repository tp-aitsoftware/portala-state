package pl.portala.statemachine.domain;

/**
 * Events that drive the {@link ApplicationStateMachine}. Each event is only
 * valid from the state(s) defined by the  stage diagram.
 */
public enum ApplicationEvent {
    /** {@code null} state -> CREATED; guard: name and body must exist (F2). */
    CREATE,
    /** CREATED -> DELETED; guard: reason must be supplied (F4). */
    DELETE,
    /** VERIFIED|ACCEPTED -> DELETED; guard: reason must be supplied (F4). */
    REJECT,
    /** CREATED -> VERIFIED; guard: createdDate exists, reason must be null. */
    VERIFY,
    /** VERIFIED -> ACCEPTED; guard: createdDate exists, reason must be null. */
    ACCEPT,
    /** ACCEPTED -> PUBLISHED; guard: objId/createdDate exist, reason null; action: assign publicationId (F5). */
    PUBLISH
}
