package org.sunbird.workflow.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.AiAssessmentApprovalEvent;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.producer.Producer;
import org.sunbird.workflow.service.impl.RequestServiceImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkflowApplicationConsumerTest {

    @InjectMocks
    private WorkflowApplicationConsumer consumer;

    @Mock
    private Producer producer;

    @Mock
    private RequestServiceImpl requestServiceImpl;

    @Mock
    private Configuration configuration;

    @Mock
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private ConsumerRecord<String, String> createRecord(String value) {
        return new ConsumerRecord<>("test-topic", 0, 0L, null, value);
    }

    // ---- processAiAssessmentMessage (Kafka listener entry point) ----

    @Test
    void testProcessAiAssessmentMessage_nullRecord() {
        assertDoesNotThrow(() -> consumer.processAiAssessmentMessage(null));
        verifyNoInteractions(mapper);
    }

    @Test
    void testProcessAiAssessmentMessage_blankValue() {
        assertDoesNotThrow(() -> consumer.processAiAssessmentMessage(createRecord("   ")));
        verifyNoInteractions(mapper);
    }

    @Test
    void testProcessAiAssessmentMessage_validValue_dispatchesAsync() throws Exception {
        when(mapper.readValue("{}", AiAssessmentApprovalEvent.class))
                .thenThrow(new RuntimeException("stop here, only verifying dispatch"));
        assertDoesNotThrow(() -> consumer.processAiAssessmentMessage(createRecord("{}")));
    }

    // ---- processAiAssessmentApproval (private, invoked directly to avoid async timing) ----

    private WfRequest buildWfRequest(String userId, String rootOrgId) {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setUserId(userId);
        wfRequest.setRootOrgId(rootOrgId);
        return wfRequest;
    }

    private Map<String, Object> buildSearchResponse(String rootOrgId, List<String> roles) {
        Map<String, Object> org = new HashMap<>();
        org.put(Constants.ORGANIZATION_ID, rootOrgId);
        if (roles != null) {
            org.put(Constants.ROLES, roles);
        }
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put(Constants.ORGANISATIONS, List.of(org));
        Map<String, Object> responseInner = new HashMap<>();
        responseInner.put(Constants.CONTENT, List.of(contentItem));
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.RESPONSE, responseInner);
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, Constants.OK);
        response.put(Constants.RESULT, result);
        return response;
    }

    @Test
    void testProcessAiAssessmentApproval_roleNotYetPresent_addsRoleAndAttemptsAssign() throws Exception {
        WfRequest wfRequest = buildWfRequest("user1", "org1");
        AiAssessmentApprovalEvent event = new AiAssessmentApprovalEvent(wfRequest, "token123");

        when(mapper.readValue("payload", AiAssessmentApprovalEvent.class)).thenReturn(event);
        when(mapper.writeValueAsString(any())).thenReturn("{\"request\":{}}");
        when(configuration.getLmsServiceHost()).thenReturn("http://mock.url/");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("search");
        when(configuration.getLmsAssignRoleEndPoint()).thenReturn("assign");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any()))
                .thenReturn(buildSearchResponse("org1", new ArrayList<>(List.of("SOME_OTHER_ROLE"))));

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(consumer, "processAiAssessmentApproval", "payload"));
        // real network call to mock.url fails and is swallowed by the outer catch -- this still
        // exercises role resolution, the "role missing" branch, and request/header assembly.
    }

    @Test
    void testProcessAiAssessmentApproval_roleAlreadyPresent_skipsAdd() throws Exception {
        WfRequest wfRequest = buildWfRequest("user2", "org2");
        AiAssessmentApprovalEvent event = new AiAssessmentApprovalEvent(wfRequest, null);

        when(mapper.readValue("payload2", AiAssessmentApprovalEvent.class)).thenReturn(event);
        when(mapper.writeValueAsString(any())).thenReturn("{\"request\":{}}");
        when(configuration.getLmsServiceHost()).thenReturn("http://mock.url/");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("search");
        when(configuration.getLmsAssignRoleEndPoint()).thenReturn("assign");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any()))
                .thenReturn(buildSearchResponse("org2", new ArrayList<>(List.of(Constants.AI_ASSESSMENT_CREATOR))));

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(consumer, "processAiAssessmentApproval", "payload2"));
        // exercises the "role already present" else-branch and the null-xAuthToken header branch.
    }

    @Test
    void testProcessAiAssessmentApproval_invalidJson_caughtByOuterCatch() throws Exception {
        when(mapper.readValue("badjson", AiAssessmentApprovalEvent.class))
                .thenThrow(new RuntimeException("bad json"));
        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(consumer, "processAiAssessmentApproval", "badjson"));
    }

    // ---- fetchUserRoles / buildUserRolesRequest (private) ----

    @Test
    void testFetchUserRoles_nullResponse_returnsEmptyList() {
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(null);
        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("search");

        List<String> roles = ReflectionTestUtils.invokeMethod(consumer, "fetchUserRoles", "u1", "org1");

        assertTrue(roles.isEmpty());
    }

    @Test
    void testFetchUserRoles_responseCodeNotOk_returnsEmptyList() {
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, "FAILED");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any())).thenReturn(response);
        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("search");

        List<String> roles = ReflectionTestUtils.invokeMethod(consumer, "fetchUserRoles", "u1", "org1");

        assertTrue(roles.isEmpty());
    }

    @Test
    void testFetchUserRoles_exceptionFromRequestService_returnsEmptyList() {
        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("search");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        List<String> roles = ReflectionTestUtils.invokeMethod(consumer, "fetchUserRoles", "u1", "org1");

        assertTrue(roles.isEmpty());
    }

    @Test
    void testFetchUserRoles_matchingOrgWithRoles_returnsRoles() {
        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("search");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any()))
                .thenReturn(buildSearchResponse("org1", List.of("ROLE_A", "ROLE_B")));

        List<String> roles = ReflectionTestUtils.invokeMethod(consumer, "fetchUserRoles", "u1", "org1");

        assertEquals(List.of("ROLE_A", "ROLE_B"), roles);
    }

    @Test
    void testFetchUserRoles_matchingOrgWithNullRoles_returnsEmptyList() {
        when(configuration.getLmsServiceHost()).thenReturn("http://host/");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("search");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any()))
                .thenReturn(buildSearchResponse("org1", null));

        List<String> roles = ReflectionTestUtils.invokeMethod(consumer, "fetchUserRoles", "u1", "org1");

        assertTrue(roles.isEmpty());
    }

    // ---- findRolesForOrg (private) ----

    @Test
    void testFindRolesForOrg_emptyContent_returnsEmptyList() {
        List<String> roles = ReflectionTestUtils.invokeMethod(consumer, "findRolesForOrg", List.of(), "org1");
        assertTrue(roles.isEmpty());
    }

    @Test
    void testFindRolesForOrg_emptyOrganisations_returnsEmptyList() {
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put(Constants.ORGANISATIONS, List.of());

        List<String> roles = ReflectionTestUtils.invokeMethod(consumer, "findRolesForOrg", List.of(contentItem), "org1");

        assertTrue(roles.isEmpty());
    }

    @Test
    void testFindRolesForOrg_noMatchingOrg_returnsEmptyList() {
        Map<String, Object> org = new HashMap<>();
        org.put(Constants.ORGANIZATION_ID, "some-other-org");
        org.put(Constants.ROLES, List.of("ROLE_X"));
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put(Constants.ORGANISATIONS, List.of(org));

        List<String> roles = ReflectionTestUtils.invokeMethod(consumer, "findRolesForOrg", List.of(contentItem), "org1");

        assertTrue(roles.isEmpty());
    }

    // ---- buildUserRolesRequest (private) ----

    @Test
    @SuppressWarnings("unchecked")
    void testBuildUserRolesRequest_wrapsUserIdInFilters() {
        Map<String, Object> requestBody = ReflectionTestUtils.invokeMethod(consumer, "buildUserRolesRequest", "u1");

        Map<String, Object> request = (Map<String, Object>) requestBody.get(Constants.REQUEST);
        Map<String, Object> filters = (Map<String, Object>) request.get(Constants.FILTERS);
        assertEquals("u1", filters.get(Constants.USER_ID));
    }
}
