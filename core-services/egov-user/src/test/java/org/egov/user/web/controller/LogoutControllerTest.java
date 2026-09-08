package org.egov.user.web.controller;

import org.egov.user.config.UserServiceConstants;
import org.egov.user.domain.model.TokenWrapper;
import org.egov.user.domain.service.UserSessionService;
import org.egov.user.persistence.repository.UserSessionLogoutEventRepository;
import org.egov.user.web.contract.auth.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.token.TokenStore;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LogoutControllerTest {

    @Mock
    private TokenStore tokenStore;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private UserSessionLogoutEventRepository userSessionLogoutEventRepository;

    private LogoutController logoutController;

    private static final String ACCESS_TOKEN = "access-token-1";
    private static final String TENANT_ID = "pb.amritsar";

    @Before
    public void setUp() {
        logoutController = new LogoutController(tokenStore, userSessionService, userSessionLogoutEventRepository);
    }

    @Test
    public void test_should_logout_session_and_remove_token_when_no_clientEventId_given() throws Exception {
        OAuth2AccessToken token = tokenWithUser(userSessionOwner());
        when(tokenStore.readAccessToken(ACCESS_TOKEN)).thenReturn(token);

        ResponseEntity<?> response = logoutController.deleteToken(tokenWrapper(null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userSessionService).logout("session-1", TENANT_ID, "user-uuid-1");
        verify(tokenStore).removeAccessToken(token);
        verify(userSessionLogoutEventRepository, never()).recordProcessed(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    public void test_should_return_success_when_access_token_already_removed() throws Exception {
        when(tokenStore.readAccessToken(ACCESS_TOKEN)).thenReturn(null);

        ResponseEntity<?> response = logoutController.deleteToken(tokenWrapper("event-1"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userSessionService, never()).logout(anyString(), anyString(), anyString());
        verify(tokenStore, never()).removeAccessToken(any(OAuth2AccessToken.class));
    }

    // Offline-logout retry: the client resends the same clientEventId after reconnecting.
    // The first delivery does the real work; a second, duplicate delivery must be a pure
    // no-op — not a second logout(), not a second event record.
    @Test
    public void test_should_process_logout_event_exactly_once_on_retry() throws Exception {
        OAuth2AccessToken token = tokenWithUser(userSessionOwner());
        when(tokenStore.readAccessToken(ACCESS_TOKEN)).thenReturn(token);
        when(userSessionLogoutEventRepository.isAlreadyProcessed("event-1", TENANT_ID)).thenReturn(false);

        ResponseEntity<?> first = logoutController.deleteToken(tokenWrapper("event-1"));
        assertEquals(HttpStatus.OK, first.getStatusCode());
        verify(userSessionService, times(1)).logout("session-1", TENANT_ID, "user-uuid-1");
        verify(userSessionLogoutEventRepository).recordProcessed(eq("event-1"), eq("session-1"), eq(TENANT_ID), eq("user-uuid-1"), anyLong());

        // Simulate the retry: token store no longer has it removed in this mock (still
        // stubbed to return the token), but the event ledger now reports the id as seen.
        when(userSessionLogoutEventRepository.isAlreadyProcessed("event-1", TENANT_ID)).thenReturn(true);

        ResponseEntity<?> retry = logoutController.deleteToken(tokenWrapper("event-1"));
        assertEquals(HttpStatus.OK, retry.getStatusCode());
        // Still only the one call from the first delivery — the retry short-circuited.
        verify(userSessionService, times(1)).logout(anyString(), anyString(), anyString());
        verify(tokenStore, times(1)).removeAccessToken(any(OAuth2AccessToken.class));
        verify(userSessionLogoutEventRepository, times(1))
                .recordProcessed(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    public void test_should_not_touch_session_or_token_store_when_event_already_recorded() throws Exception {
        OAuth2AccessToken token = tokenWithUser(userSessionOwner());
        when(tokenStore.readAccessToken(ACCESS_TOKEN)).thenReturn(token);
        when(userSessionLogoutEventRepository.isAlreadyProcessed("event-1", TENANT_ID)).thenReturn(true);

        ResponseEntity<?> response = logoutController.deleteToken(tokenWrapper("event-1"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userSessionService, never()).logout(anyString(), anyString(), anyString());
        verify(tokenStore, never()).removeAccessToken(any(OAuth2AccessToken.class));
        verify(userSessionLogoutEventRepository, never())
                .recordProcessed(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    private User userSessionOwner() {
        return User.builder()
                .uuid("user-uuid-1")
                .tenantId(TENANT_ID)
                .sessionId("session-1")
                .build();
    }

    private OAuth2AccessToken tokenWithUser(User user) {
        DefaultOAuth2AccessToken token = new DefaultOAuth2AccessToken(ACCESS_TOKEN);
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put(UserServiceConstants.USER_REQUEST_KEY, user);
        token.setAdditionalInformation(additionalInfo);
        return token;
    }

    private TokenWrapper tokenWrapper(String clientEventId) {
        TokenWrapper tokenWrapper = new TokenWrapper();
        tokenWrapper.setAccessToken(ACCESS_TOKEN);
        tokenWrapper.setClientEventId(clientEventId);
        return tokenWrapper;
    }
}
