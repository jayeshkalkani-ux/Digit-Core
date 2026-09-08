-- Idempotency ledger for offline-queued logout events. A client generates one
-- clienteventid per offline logout and retries /_logout with the same id until it succeeds;
-- the primary key here is what turns those retries into an exactly-once effect.
CREATE TABLE IF NOT EXISTS eg_user_session_logout_event (
    clienteventid character varying(128) PRIMARY KEY,
    sessionid character varying(64),
    tenantid character varying(256) NOT NULL,
    useruuid character varying(64) NOT NULL,
    processedtime bigint NOT NULL
);
