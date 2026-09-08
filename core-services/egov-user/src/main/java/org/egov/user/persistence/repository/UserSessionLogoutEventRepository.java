package org.egov.user.persistence.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.user.repository.builder.UserSessionLogoutEventQueryBuilder;
import org.egov.user.utils.DatabaseSchemaUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Idempotency ledger for offline-queued logout events (see LogoutController). A client
 * generates one clientEventId per offline logout and may retry /_logout with that same id
 * any number of times before it lands; this repository is what turns those retries into an
 * exactly-once effect on the session and the audit trail.
 */
@Repository
@Slf4j
public class UserSessionLogoutEventRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final DatabaseSchemaUtils databaseSchemaUtils;

    public UserSessionLogoutEventRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                             DatabaseSchemaUtils databaseSchemaUtils) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.databaseSchemaUtils = databaseSchemaUtils;
    }

    public boolean isAlreadyProcessed(String clientEventId, String tenantId) {
        String query = databaseSchemaUtils.replaceSchemaPlaceholder(
                UserSessionLogoutEventQueryBuilder.SELECT_LOGOUT_EVENT_SQL, tenantId);
        return !namedParameterJdbcTemplate.queryForList(query,
                Collections.singletonMap("clienteventid", clientEventId)).isEmpty();
    }

    /**
     * Best-effort: called only after the logout it records has already taken effect, so a
     * failure here (including a losing race against a concurrent duplicate call, surfaced as
     * {@link DuplicateKeyException} on the clientEventId primary key) must never fail the
     * request — the logout itself already succeeded.
     */
    public void recordProcessed(String clientEventId, String sessionId, String tenantId, String userUuid, long now) {
        Map<String, Object> params = new HashMap<>();
        params.put("clienteventid", clientEventId);
        params.put("sessionid", sessionId);
        params.put("tenantid", tenantId);
        params.put("useruuid", userUuid);
        params.put("processedtime", now);

        String query = databaseSchemaUtils.replaceSchemaPlaceholder(
                UserSessionLogoutEventQueryBuilder.INSERT_LOGOUT_EVENT_SQL, tenantId);
        try {
            namedParameterJdbcTemplate.update(query, params);
        } catch (DuplicateKeyException e) {
            log.info("Logout event {} already recorded by a concurrent request", clientEventId);
        }
    }
}
