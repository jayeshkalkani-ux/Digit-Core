package org.egov.user.persistence.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@NoArgsConstructor
public class UserSession {

    private String userUuid;
    private String tenantId;
    private String deviceId;
    private String sessionId;
    private String status;
    private long createdTime;
    private long lastServerContact;
    // Optimistic-concurrency guard: incremented on every mutating write (touch, reactivate,
    // expire, status change). A read-then-conditionally-write flow (see
    // UserSessionService#expireIfStale) passes the version it read back into its UPDATE's
    // WHERE clause, so the write only applies if nothing else changed the row in between.
    private int version;

    // Kept alongside the no-args constructor (rather than @AllArgsConstructor) so every
    // existing call site that predates the version field keeps compiling unchanged, with new
    // sessions always starting at version 0. Rows read back from the DB get their real
    // version via BeanPropertyRowMapper's setter, bypassing this constructor entirely.
    public UserSession(String userUuid, String tenantId, String deviceId, String sessionId,
                        String status, long createdTime, long lastServerContact) {
        this.userUuid = userUuid;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.sessionId = sessionId;
        this.status = status;
        this.createdTime = createdTime;
        this.lastServerContact = lastServerContact;
        this.version = 0;
    }
}
