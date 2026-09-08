package org.egov.user.persistence.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.user.persistence.dto.UserSessionAudit;
import org.egov.user.repository.builder.UserSessionAuditQueryBuilder;
import org.egov.user.utils.DatabaseSchemaUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

/**
 * Append-only audit trail for single-active-login session transitions (compliance/
 * traceability). Purely a write path — nothing in this feature reads it back yet, so no
 * query methods exist. A write failure here must never fail the request it's auditing;
 * see UserSessionService.recordAudit, which never lets an insert exception propagate.
 */
@Repository
@Slf4j
public class UserSessionAuditRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final DatabaseSchemaUtils databaseSchemaUtils;

    public UserSessionAuditRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                       DatabaseSchemaUtils databaseSchemaUtils) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.databaseSchemaUtils = databaseSchemaUtils;
    }

    public void insert(UserSessionAudit audit) {
        Map<String, Object> params = new HashMap<>();
        params.put("useruuid", audit.getUserUuid());
        params.put("tenantid", audit.getTenantId());
        params.put("deviceid", audit.getDeviceId());
        params.put("sessionid", audit.getSessionId());
        params.put("action", audit.getAction());
        params.put("actor", audit.getActor());
        params.put("eventtime", audit.getEventTime());
        params.put("details", audit.getDetails());

        String query = databaseSchemaUtils.replaceSchemaPlaceholder(
                UserSessionAuditQueryBuilder.INSERT_SESSION_AUDIT_SQL, audit.getTenantId());
        namedParameterJdbcTemplate.update(query, params);
    }
}
