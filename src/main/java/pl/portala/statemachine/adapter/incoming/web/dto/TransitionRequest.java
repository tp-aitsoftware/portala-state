package pl.portala.statemachine.adapter.incoming.web.dto;

import jakarta.validation.constraints.NotBlank;
import pl.portala.statemachine.domain.ApplicationReason;

/**
 * POST /api/applications/{objId}/events body.
 *
 * <p>The event drives the state machine; author is required by the guards of
 * VERIFY/ACCEPT/PUBLISH/DELETE/REJECT (R4) and reason is required on DELETE/REJECT
 * (F4). Whether a field is mandatory is enforced by the domain guard, not here, so
 * that the same endpoint serves every event.
 */
public record TransitionRequest(
        @NotBlank(message = "event is required") String event,
        String author,
        ApplicationReason reason,
        long expectedVersion
) {
}
