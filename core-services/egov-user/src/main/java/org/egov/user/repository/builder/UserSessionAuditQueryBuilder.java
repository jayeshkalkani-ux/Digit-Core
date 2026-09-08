package org.egov.user.repository.builder;

import static org.egov.user.utils.DatabaseSchemaUtils.SCHEMA_REPLACE_STRING;

/**
 * SQL for the append-only single-active-login audit trail (eg_user_session_audit). Schema-
 * templated the same way as {@link UserSessionQueryBuilder}, for central-instance compatibility.
 */
public final class UserSessionAuditQueryBuilder {

    private UserSessionAuditQueryBuilder() {
    }

    public static final String INSERT_SESSION_AUDIT_SQL =
            "INSERT INTO " + SCHEMA_REPLACE_STRING + ".eg_user_session_audit " +
            "(useruuid, tenantid, deviceid, sessionid, action, actor, eventtime, details) " +
            "VALUES (:useruuid, :tenantid, :deviceid, :sessionid, :action, :actor, :eventtime, :details)";

}
