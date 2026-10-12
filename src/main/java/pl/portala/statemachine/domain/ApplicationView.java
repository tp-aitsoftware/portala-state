package pl.portala.statemachine.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-model projection of one {@link Application} snapshot (A6 - the view stack is
 * separate from the append-only write stack).
 */
public record ApplicationView(
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

    public static ApplicationView of(Application application) {
        return new ApplicationView(
                application.id(),
                application.objId(),
                application.createdDate(),
                application.version(),
                application.state(),
                application.name(),
                application.body(),
                application.author(),
                application.reason(),
                application.publicationId());
    }
}
