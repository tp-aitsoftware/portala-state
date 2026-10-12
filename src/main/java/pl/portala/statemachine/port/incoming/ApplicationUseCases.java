package pl.portala.statemachine.port.incoming;

import pl.portala.statemachine.domain.ApplicationReason;
import pl.portala.statemachine.domain.ApplicationView;
import pl.portala.statemachine.domain.PageResult;

import java.util.UUID;

/**
 * Use cases of the service as seen from the driving side (REST is one adapter,
 * tests with a mocked repository are another).
 */
public interface ApplicationUseCases {

    /** creates the request in state CREATED with version 0. */
    ApplicationView create(String name, String body, String author);

    /** edits the content; only allowed in CREATED / VERIFIED. */
    ApplicationView editContent(UUID objId, String name, String body, String author, long expectedVersion);

    /** Fires a workflow event (VERIFY / ACCEPT / PUBLISH / DELETE / REJECT) on the latest version. */
    ApplicationView applyEvent(UUID objId, String eventName, String author, ApplicationReason reason, long expectedVersion);

    /** Current (highest version) snapshot of one request. */
    ApplicationView find(UUID objId);

    /** the complete append-only history of one request. */
    java.util.List<ApplicationView> history(UUID objId);

    /** listing with pagination (offset based, page size default 10) and name / state filters. */
    PageResult<ApplicationView> list(String nameFilter, String stateFilter, int page, int size);
}
