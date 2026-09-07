package org.egov.user.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenWrapper {

    @JsonProperty("access_token")
    private String accessToken;

    // Client-generated id for one offline-queued logout event. Optional — a normal online
    // logout doesn't need it since it's only ever sent once. When present, it lets a retried
    // /_logout call (the app resending its offline logout queue after reconnecting) be
    // recognized as a duplicate and short-circuited instead of reprocessed — see
    // UserSessionLogoutEventRepository.
    @JsonProperty("client_event_id")
    private String clientEventId;

}
