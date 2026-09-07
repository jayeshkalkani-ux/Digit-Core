package org.egov.user.web.controller;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.tracer.model.CustomException;
import org.egov.user.domain.service.UserSessionService;
import org.egov.user.web.contract.RevokeSessionRequest;
import org.egov.user.web.contract.factory.ResponseInfoFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers AC9 (admin revoke immediately frees the user ID) and validation item 12
 * (admin can revoke an active session) at the controller/API level, complementing the
 * service-level coverage already in UserSessionServiceTest.
 */
@RunWith(MockitoJUnitRunner.class)
public class UserSessionControllerTest {

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private ResponseInfoFactory responseInfoFactory;

    private UserSessionController userSessionController;

    private static final String USER_UUID = "user-uuid-1";
    private static final String TENANT_ID = "pb.amritsar";
    private static final String ADMIN_UUID = "admin-uuid-1";

    @Before
    public void setUp() {
        userSessionController = new UserSessionController(userSessionService, responseInfoFactory);
    }

    @Test
    public void test_should_revoke_session_and_return_ok_with_actor_from_requestInfo() {
        RevokeSessionRequest request = revokeRequest(ADMIN_UUID);
        ResponseInfo responseInfo = new ResponseInfo("", "", System.currentTimeMillis(), "", "", "successful");
        when(responseInfoFactory.createResponseInfoFromRequestInfo(eq(request.getRequestInfo()), eq(true)))
                .thenReturn(responseInfo);

        ResponseEntity<Map<String, Object>> response = userSessionController.revoke(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(responseInfo, response.getBody().get("ResponseInfo"));
        verify(userSessionService).revoke(USER_UUID, TENANT_ID, ADMIN_UUID);
    }

    // No caller identity on the request — falls back to a sentinel actor rather than failing
    // the revoke or attributing it to the wrong admin in the audit trail.
    @Test
    public void test_should_fallback_to_unknown_admin_when_requestInfo_has_no_userInfo() {
        RevokeSessionRequest request = new RevokeSessionRequest();
        request.setUserUuid(USER_UUID);
        request.setTenantId(TENANT_ID);
        request.setRequestInfo(RequestInfo.builder().build());

        userSessionController.revoke(request);

        verify(userSessionService).revoke(USER_UUID, TENANT_ID, "UNKNOWN_ADMIN");
    }

    @Test
    public void test_should_fallback_to_unknown_admin_when_requestInfo_is_null() {
        RevokeSessionRequest request = new RevokeSessionRequest();
        request.setUserUuid(USER_UUID);
        request.setTenantId(TENANT_ID);

        userSessionController.revoke(request);

        verify(userSessionService).revoke(USER_UUID, TENANT_ID, "UNKNOWN_ADMIN");
    }

    // Validation item 12 / AC9's negative counterpart: revoking a user with no active session
    // must not silently succeed — the exception propagates for the standard error handler to
    // report, instead of being swallowed here.
    @Test(expected = CustomException.class)
    public void test_should_propagate_exception_when_no_active_session_to_revoke() {
        RevokeSessionRequest request = revokeRequest(ADMIN_UUID);
        doThrow(new CustomException("NO_ACTIVE_SESSION", "No active session found"))
                .when(userSessionService).revoke(eq(USER_UUID), eq(TENANT_ID), any(String.class));

        userSessionController.revoke(request);
    }

    private RevokeSessionRequest revokeRequest(String adminUuid) {
        org.egov.common.contract.request.User adminInfo = org.egov.common.contract.request.User.builder()
                .uuid(adminUuid).build();
        RevokeSessionRequest request = new RevokeSessionRequest();
        request.setUserUuid(USER_UUID);
        request.setTenantId(TENANT_ID);
        request.setRequestInfo(RequestInfo.builder().userInfo(adminInfo).build());
        return request;
    }
}
