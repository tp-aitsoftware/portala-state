package pl.portala.statemachine.port.outgoing;

import pl.portala.statemachine.domain.Application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven port for the write side of the CQRS split: the aggregate history is
 * append-only, so there is no update and no delete - a DELETED request simply gets
 * one more row.
 */
public interface ApplicationRepository {

    /** Inserts the given snapshot, which must be exactly one version above {@code expectedVersion}. */
    Application append(Application application);

    Optional<Application> findLatest(UUID objId);

    Optional<Application> findByObjIdAndVersion(UUID objId, long version);

    List<Application> findAllVersions(UUID objId);
}
