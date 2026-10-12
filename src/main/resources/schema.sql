-- PORTAL-STATE-1 schema (A4). Applied by Spring's spring.sql.init on startup and in
-- the Testcontainers-based integration tests. Idempotent so a restart is safe.
--
-- The 'application' table is APPEND-ONLY (F6): one row per (obj_id, version); every
-- state change or content edit inserts a new row with version + 1 under the same
-- obj_id. Nothing is ever UPDATE-d or DELETE-d - DELETED is a state, a soft delete
-- (R2). Optimistic locking is manual: unique (obj_id, version) makes a concurrent
-- append of the same version fail, which the adapter turns into a conflict.

CREATE TABLE IF NOT EXISTS application (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    obj_id          UUID           NOT NULL,
    created_date    TIMESTAMP      NOT NULL,
    version         BIGINT         NOT NULL,
    state           VARCHAR(16)    NOT NULL,
    name            VARCHAR(255),
    body            TEXT,
    author          VARCHAR(255),
    reason          VARCHAR(32),
    publication_id  BIGINT,
    CONSTRAINT uq_application_obj_version UNIQUE (obj_id, version),
    CONSTRAINT ck_application_state CHECK (
        state IN ('CREATED', 'VERIFIED', 'ACCEPTED', 'PUBLISHED', 'DELETED')
    ),
    CONSTRAINT ck_application_reason CHECK (
        reason IS NULL OR reason IN ('FINISH_ON_DELETE', 'FINISH_ON_REJECTED')
    )
);

-- F7 browsing: latest-version lookup per obj_id, and filters by name / state.
CREATE INDEX IF NOT EXISTS ix_application_obj_version ON application (obj_id, version DESC);
CREATE INDEX IF NOT EXISTS ix_application_state ON application (state);
CREATE INDEX IF NOT EXISTS ix_application_name_lower ON application (LOWER(name));

-- F5 publication id: a single PostgreSQL sequence read from Java (decision - see README).
CREATE SEQUENCE IF NOT EXISTS application_publication_id_seq START WITH 1 INCREMENT BY 1;
