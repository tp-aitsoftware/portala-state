package pl.portala.statemachine.domain;

import java.util.UUID;

/** No row exists for the requested objId (maps to HTTP 404). */
public class ApplicationNotFoundException extends ApplicationException {

    public ApplicationNotFoundException(UUID objId) {
        super("No request found with objId %s".formatted(objId));
    }
}
