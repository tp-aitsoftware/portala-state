package pl.portala.statemachine.port.outgoing;

/**
 * Abstraction for publishing assigns a unique numeric identifier. The strategy chosen for
 * this project is a plain PostgreSQL sequence read from Java (see README.md,
 * assumptions section: the sequence may produce gaps if a publish rolls back).
 */
public interface PublicationIdGenerator {

    long nextPublicationId();
}
