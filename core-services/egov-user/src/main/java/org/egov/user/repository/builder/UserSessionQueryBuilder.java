package org.egov.user.repository.builder;

import static org.egov.user.utils.DatabaseSchemaUtils.SCHEMA_REPLACE_STRING;

/**
 * SQL for the single-active-login session table (eg_user_session). Every statement is
 * schema-templated via {@link org.egov.user.utils.DatabaseSchemaUtils#SCHEMA_REPLACE_STRING},
 * matching the pattern used by UserRepository/RoleRepository/BulkUserRepository for
 * central-instance compatibility.
 */
public final class UserSessionQueryBuilder {

    private UserSessionQueryBuilder() {
    }

    public static final String INSERT_ACTIVE_SESSION_SQL =
            "INSERT INTO " + SCHEMA_REPLACE_STRING + ".eg_user_session " +
            "(useruuid, tenantid, deviceid, sessionid, status, createdtime, lastservercontact, version) " +
            "VALUES (:useruuid, :tenantid, :deviceid, :sessionid, :status, :createdtime, :lastservercontact, :version)";

    public static final String SELECT_SESSION_BY_SESSIONID_SQL =
            "SELECT useruuid, tenantid, deviceid, sessionid, status, createdtime, lastservercontact, version " +
            "FROM " + SCHEMA_REPLACE_STRING + ".eg_user_session WHERE sessionid = :sessionid";

    public static final String SELECT_ACTIVE_SESSION_BY_USER_TENANT_SQL =
            "SELECT useruuid, tenantid, deviceid, sessionid, status, createdtime, lastservercontact, version " +
            "FROM " + SCHEMA_REPLACE_STRING + ".eg_user_session " +
            "WHERE useruuid = :useruuid AND tenantid = :tenantid AND status = 'ACTIVE'";

    // Only transitions a row that is currently ACTIVE — a stale/already-terminated session
    // is left untouched instead of being re-stamped with a new terminal status.
    public static final String UPDATE_SESSION_STATUS_SQL =
            "UPDATE " + SCHEMA_REPLACE_STRING + ".eg_user_session SET status = :status, version = version + 1 " +
            "WHERE sessionid = :sessionid AND status = 'ACTIVE'";

    // Single atomic, conditional write: only applies (and only costs a write) when the stored
    // lastservercontact is older than the debounce window, so no separate read is needed to
    // decide whether to update it.
    public static final String TOUCH_LAST_SERVER_CONTACT_SQL =
            "UPDATE " + SCHEMA_REPLACE_STRING + ".eg_user_session SET lastservercontact = :now, version = version + 1 " +
            "WHERE sessionid = :sessionid AND status = 'ACTIVE' AND lastservercontact < :staleBefore";

    // Re-login on the same device: rotates the sessionId and resets the timestamps on the
    // existing ACTIVE row instead of inserting a new one, so it never collides with the
    // partial unique index. The deviceid match in the WHERE clause (not just in application
    // code) is what keeps this atomic against a concurrent different-device login.
    public static final String REACTIVATE_SESSION_FOR_DEVICE_SQL =
            "UPDATE " + SCHEMA_REPLACE_STRING + ".eg_user_session " +
            "SET sessionid = :newsessionid, createdtime = :now, lastservercontact = :now, version = version + 1 " +
            "WHERE useruuid = :useruuid AND tenantid = :tenantid AND deviceid = :deviceid AND status = 'ACTIVE'";

    // Lazily expires a session that has gone stale beyond the configured inactivity window.
    // Conditional on status = 'ACTIVE', lastservercontact age, AND version — the version check
    // is the optimistic-concurrency guard: it's read by the caller (see
    // UserSessionService#expireIfStale) alongside the row that decided this session looks
    // stale, so if anything else mutated the row since that read (a touch, a reactivation, a
    // revoke), this UPDATE matches zero rows instead of expiring a row that has moved on.
    public static final String EXPIRE_STALE_SESSION_SQL =
            "UPDATE " + SCHEMA_REPLACE_STRING + ".eg_user_session SET status = 'EXPIRED', version = version + 1 " +
            "WHERE sessionid = :sessionid AND status = 'ACTIVE' AND lastservercontact < :cutoff AND version = :expectedversion";

}
