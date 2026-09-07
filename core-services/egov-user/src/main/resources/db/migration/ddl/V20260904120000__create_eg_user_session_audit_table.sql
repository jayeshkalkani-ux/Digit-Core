CREATE TABLE IF NOT EXISTS eg_user_session_audit (
    id bigserial PRIMARY KEY,
    useruuid character varying(64) NOT NULL,
    tenantid character varying(256) NOT NULL,
    deviceid character varying(256),
    sessionid character varying(64),
    action character varying(32) NOT NULL,
    actor character varying(128) NOT NULL,
    eventtime bigint NOT NULL,
    details character varying(512)
);

-- Lookup by user/tenant to reconstruct a user's session history for an audit/support query.
CREATE INDEX IF NOT EXISTS idx_eg_user_session_audit_user_tenant ON eg_user_session_audit (useruuid, tenantid);

-- Lookup by sessionid to trace all transitions of one specific session.
CREATE INDEX IF NOT EXISTS idx_eg_user_session_audit_sessionid ON eg_user_session_audit (sessionid);
