package pl.portala.statemachine.port.outgoing;

import pl.portala.statemachine.domain.ApplicationState;
import pl.portala.statemachine.domain.ApplicationView;
import pl.portala.statemachine.domain.PageResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Driven port for the read side. Queries never touch the aggregate; they work
 * directly on the projection.
 *
 * <p>Because the table is append-only, "the requests" means the highest-version row
 * of every objId. On state DELETED rows are returned only when DELETED is given as the
 * state filter, they are never hidden from an explicit query.
 */
public interface ApplicationQueryRepository {

    Optional<ApplicationView> findLatest(UUID objId);

    PageResult<ApplicationView> findCurrentVersions(String nameContainsIgnoreCase, ApplicationState state, int page, int size);
}
