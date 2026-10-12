package pl.portala.statemachine.adapter.outgoing.jdbc;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import pl.portala.statemachine.domain.Application;
import pl.portala.statemachine.domain.ApplicationState;
import pl.portala.statemachine.domain.ApplicationView;
import pl.portala.statemachine.domain.PageResult;
import pl.portala.statemachine.port.outgoing.ApplicationQueryRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC driven adapter for the read side (CQRS query stack ). It projects the
 * current (highest-version) row of every objId and applies the  filters. Filters
 * are appended dynamically instead of using {@code ? IS NULL} guards so PostgreSQL
 * never has to infer the type of a null parameter.
 *
 * <p>Decision  : offset pagination, page index is 0-based, name filter is a
 * case-insensitive {@code LIKE %term%}, results are ordered by created_date DESC
 * then objId for a stable order.
 *
 * <p>Decision : the browsing query hides soft-deleted requests unless DELETED is
 * explicitly requested as the state filter - so a null filter means "every live state",
 * while {@code state = DELETED} returns the deleted ones.
 */
@Component
public class JdbcApplicationQueryRepository implements ApplicationQueryRepository {

    /** Derived table: one row per objId holding only its newest version. */
    private static final String CURRENT_VERSIONS = """
            SELECT DISTINCT ON (obj_id) id, obj_id, created_date, version, state, name, body, author, reason, publication_id
            FROM application
            ORDER BY obj_id, version DESC
            """;

    private final JdbcClient jdbc;

    public JdbcApplicationQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ApplicationView> findLatest(UUID objId) {
        return jdbc.sql("""
                        SELECT id, obj_id, created_date, version, state, name, body, author, reason, publication_id
                        FROM application WHERE obj_id = ? ORDER BY version DESC LIMIT 1
                        """)
                .param(objId)
                .query(ApplicationRowMapper::map)
                .optional()
                .map(ApplicationView::of);
    }

    @Override
    public PageResult<ApplicationView> findCurrentVersions(String nameContainsIgnoreCase, ApplicationState state, int page, int size) {
        List<String> conditions = new ArrayList<>();
        List<Object> filterParams = new ArrayList<>();
        if (nameContainsIgnoreCase != null && !nameContainsIgnoreCase.isBlank()) {
            conditions.add("LOWER(cur.name) LIKE ?");
            filterParams.add("%" + nameContainsIgnoreCase.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (state != null) {
            conditions.add("cur.state = ?");
            filterParams.add(state.name());
        } else {
            // R3: soft-deleted requests are excluded unless DELETED is asked for explicitly
            conditions.add("cur.state <> 'DELETED'");
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        long total = jdbc.sql("SELECT COUNT(*) FROM (" + CURRENT_VERSIONS + ") cur" + where)
                .params(filterParams)
                .query(Long.class)
                .single();

        int offset = page * size;
        List<ApplicationView> items = jdbc.sql(
                        "SELECT cur.id, cur.obj_id, cur.created_date, cur.version, cur.state, cur.name, cur.body, cur.author, cur.reason, cur.publication_id"
                                + " FROM (" + CURRENT_VERSIONS + ") cur" + where
                                + " ORDER BY cur.created_date DESC, cur.obj_id LIMIT ? OFFSET ?")
                .params(concat(filterParams, List.of(size, offset)))
                .query(ApplicationRowMapper::map)
                .list()
                .stream()
                .map(ApplicationView::of)
                .toList();

        boolean hasNext = (long) offset + items.size() < total;
        return new PageResult<>(items, page, size, total, hasNext);
    }

    private static List<Object> concat(List<Object> first, List<Object> second) {
        List<Object> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
