package pl.portala.statemachine.adapter.outgoing.jdbc;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import pl.portala.statemachine.domain.Application;
import pl.portala.statemachine.domain.OptimisticLockConflictException;
import pl.portala.statemachine.port.outgoing.ApplicationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC driven adapter for the append-only write side ,  actually
 * enforces optimistic locking under concurrency - a clash is translated into
 * {@link OptimisticLockConflictException}.
 */
@Component
public class JdbcApplicationRepository implements ApplicationRepository {

    // PostgreSQL RETURNING gives us the identity straight from the append (no JPA keys).
    private static final String INSERT = """
            INSERT INTO application (obj_id, created_date, version, state, name, body, author, reason, publication_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String SELECT_COLUMNS = """
            SELECT id, obj_id, created_date, version, state, name, body, author, reason, publication_id
            FROM application
            """;

    private final JdbcClient jdbc;

    public JdbcApplicationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Application append(Application application) {
        try {
            Long generatedId = jdbc.sql(INSERT)
                    .params(
                            application.objId(),
                            application.createdDate(),
                            application.version(),
                            application.state().name(),
                            application.name(),
                            application.body(),
                            application.author(),
                            ApplicationRowMapper.toReasonColumn(application),
                            application.publicationId())
                    .query(Long.class)
                    .single();
            return application.withId(generatedId);
        } catch (DuplicateKeyException e) {
            // somebody appended the same version first - the optimistic-lock guard trips
            long currentVersion = maxVersion(application.objId());
            throw new OptimisticLockConflictException(application.objId(), application.version() - 1, currentVersion);
        }
    }

    @Override
    public Optional<Application> findLatest(UUID objId) {
        return jdbc.sql(SELECT_COLUMNS + " WHERE obj_id = ? ORDER BY version DESC LIMIT 1")
                .param(objId)
                .query(ApplicationRowMapper::map)
                .optional();
    }

    @Override
    public Optional<Application> findByObjIdAndVersion(UUID objId, long version) {
        return jdbc.sql(SELECT_COLUMNS + " WHERE obj_id = ? AND version = ?")
                .params(objId, version)
                .query(ApplicationRowMapper::map)
                .optional();
    }

    @Override
    public List<Application> findAllVersions(UUID objId) {
        return jdbc.sql(SELECT_COLUMNS + " WHERE obj_id = ? ORDER BY version ASC")
                .param(objId)
                .query(ApplicationRowMapper::map)
                .list();
    }

    private long maxVersion(UUID objId) {
        return findLatest(objId).map(Application::version).orElse(-1L);
    }
}
