package pl.portala.statemachine.adapter.incoming.web.dto;

import pl.portala.statemachine.domain.ApplicationReason;
import pl.portala.statemachine.domain.ApplicationState;
import pl.portala.statemachine.domain.ApplicationView;

import java.time.LocalDateTime;
import java.util.UUID;

/** Public representation of one application snapshot (decoupled from the read model). */
public record ApplicationResponse(
        Long id,
        UUID objId,
        LocalDateTime createdDate,
        long version,
        ApplicationState state,
        String name,
        String body,
        String author,
        ApplicationReason reason,
        Long publicationId
) {

    public static ApplicationResponse from(ApplicationView view) {
        return new ApplicationResponse(
                view.id(), view.objId(), view.createdDate(), view.version(), view.state(),
                view.name(), view.body(), view.author(), view.reason(), view.publicationId());
    }
}
