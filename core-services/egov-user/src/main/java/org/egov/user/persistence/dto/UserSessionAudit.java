package org.egov.user.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserSessionAudit {

    private String userUuid;
    private String tenantId;
    private String deviceId;
    private String sessionId;
    private String action;
    private String actor;
    private long eventTime;
    private String details;
}
