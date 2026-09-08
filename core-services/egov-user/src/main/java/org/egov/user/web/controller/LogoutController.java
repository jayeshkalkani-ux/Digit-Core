package org.egov.user.web.controller;

import org.egov.common.contract.response.Error;
import org.egov.common.contract.response.ErrorResponse;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.user.config.UserServiceConstants;
import org.egov.user.domain.model.TokenWrapper;
import org.egov.user.domain.service.UserSessionService;
import org.egov.user.persistence.repository.UserSessionLogoutEventRepository;
import org.egov.user.web.contract.auth.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogoutController {

    private final TokenStore tokenStore;
    private final UserSessionService userSessionService;
    private final UserSessionLogoutEventRepository userSessionLogoutEventRepository;

    public LogoutController(TokenStore tokenStore, UserSessionService userSessionService,
                             UserSessionLogoutEventRepository userSessionLogoutEventRepository) {
        this.tokenStore = tokenStore;
        this.userSessionService = userSessionService;
        this.userSessionLogoutEventRepository = userSessionLogoutEventRepository;
    }

    /**
     * End-point to logout the session. Idempotent — a device that logs out while offline
     * clears its local session immediately and queues this call with a client-generated
     * {@code clientEventId}, resent on reconnect (possibly more than once, on retry). Two
     * things make repeated calls safe to replay:
     * <ol>
     *   <li>the underlying state change is itself a no-op once applied (see
     *       UserSessionService#logout, guarded by "still ACTIVE"), and</li>
     *   <li>when {@code clientEventId} is supplied, a duplicate is recognized up front via
     *       {@link UserSessionLogoutEventRepository} and short-circuited before touching the
     *       session or token store at all.</li>
     * </ol>
     */
    @PostMapping("/_logout")
    public ResponseEntity<?> deleteToken(@RequestBody TokenWrapper tokenWrapper) throws Exception {
        String accessToken = tokenWrapper.getAccessToken();
        OAuth2AccessToken redisToken = tokenStore.readAccessToken(accessToken);
        if (redisToken == null) {
            // Already gone — either this token was never valid, or a prior attempt (possibly
            // this exact retry) already completed the logout. Either way the goal of a logout
            // call ("this token is not authenticated") already holds, so a retry must see
            // success here — an offline-logout sync queue that got "Logout failed" for an
            // already-completed logout would keep retrying it indefinitely.
            return buildLogoutSuccessResponse();
        }

        String clientEventId = tokenWrapper.getClientEventId();
        User user = extractUser(redisToken);
        if (user != null && clientEventId != null
                && userSessionLogoutEventRepository.isAlreadyProcessed(clientEventId, user.getTenantId())) {
            return buildLogoutSuccessResponse();
        }

        if (user != null) {
            userSessionService.logout(user.getSessionId(), user.getTenantId(), user.getUuid());
        }
        tokenStore.removeAccessToken(redisToken);
        if (user != null && clientEventId != null) {
            userSessionLogoutEventRepository.recordProcessed(clientEventId, user.getSessionId(), user.getTenantId(),
                    user.getUuid(), System.currentTimeMillis());
        }
        return buildLogoutSuccessResponse();
    }

    private User extractUser(OAuth2AccessToken redisToken) {
        if (redisToken.getAdditionalInformation() == null) {
            return null;
        }
        Object userRequestObj = redisToken.getAdditionalInformation().get(UserServiceConstants.USER_REQUEST_KEY);
        return userRequestObj instanceof User ? (User) userRequestObj : null;
    }

    private ResponseEntity<ResponseInfo> buildLogoutSuccessResponse() {
        ResponseInfo responseInfo = new ResponseInfo("", "", System.currentTimeMillis(), "", "", "Logout successfully");
        return new ResponseEntity<>(responseInfo, HttpStatus.OK);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleError(Exception ex) {
        ex.printStackTrace();
        return buildLogoutFailedResponse();
    }

    private ResponseEntity<ErrorResponse> buildLogoutFailedResponse() {
        ErrorResponse response = new ErrorResponse();
        ResponseInfo responseInfo = new ResponseInfo("", "", System.currentTimeMillis(), "", "", "Logout failed");
        response.setResponseInfo(responseInfo);
        Error error = new Error();
        error.setCode(400);
        error.setDescription("Logout failed");
        response.setError(error);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}