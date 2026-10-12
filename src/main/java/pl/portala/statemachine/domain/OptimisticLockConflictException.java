package pl.portala.statemachine.domain;

import java.util.UUID;

/**
 * Raised when the row that was loaded is no longer the newest one for that objId -
 * somebody else appended a version meanwhile. This is the manual optimistic locking
 * - the client has to re-read and retry.
 * Maps to HTTP 412.
 */
public class OptimisticLockConflictException extends ApplicationException {

    private final UUID objId;
    private final long expectedVersion;
    private final long currentVersion;

    public OptimisticLockConflictException(UUID objId, long expectedVersion, long currentVersion) {
        super("Concurrent change on objId %s: expected version %d but the newest stored version is %d"
                .formatted(objId, expectedVersion, currentVersion));
        this.objId = objId;
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public UUID objId() {
        return objId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
