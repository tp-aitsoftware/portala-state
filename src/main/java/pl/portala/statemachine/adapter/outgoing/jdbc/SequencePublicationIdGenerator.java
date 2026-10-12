package pl.portala.statemachine.adapter.outgoing.jdbc;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import pl.portala.statemachine.port.outgoing.PublicationIdGenerator;

/**
 * Reads the next value of the PostgreSQL sequence {@code application_publication_id_seq} .
 */
@Component
public class SequencePublicationIdGenerator implements PublicationIdGenerator {

    private final JdbcClient jdbc;

    public SequencePublicationIdGenerator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nextPublicationId() {
        return jdbc.sql("SELECT nextval('application_publication_id_seq')")
                .query(Long.class)
                .single();
    }
}
