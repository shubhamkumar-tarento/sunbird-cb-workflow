package org.sunbird.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.exception.ApplicationException;
import org.sunbird.workflow.exception.BadRequestException;
import org.sunbird.workflow.exception.InvalidDataInputException;
import org.sunbird.workflow.models.Response;
import org.sunbird.workflow.models.SearchCriteria;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.postgres.entity.WfStatusCountDTO;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfAuditRepo;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.producer.Producer;
import org.sunbird.workflow.service.impl.RequestServiceImpl;
import org.sunbird.workflow.utils.AccessTokenValidator;
import org.sunbird.workflow.utils.CassandraOperation;
import org.sunbird.workflow.utils.ElasticsearchServiceManager;
import org.sunbird.workflow.utils.LRUCache;
import org.sunbird.workflow.utils.NotificationTriggerService;
import org.sunbird.workflow.utils.UserUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAssessmentServiceImplTest {

    @Mock
    private WfStatusRepo wfStatusRepo;
    @Mock
    private WfAuditRepo wfAuditRepo;
    @Mock
    private ObjectMapper mapper;
    @Mock
    private Configuration configuration;
    @Mock
    private UserProfileWfService userProfileWfService;
    @Mock
    private RequestServiceImpl requestServiceImpl;
    @Mock
    private Producer producer;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private AccessTokenValidator accessTokenValidator;
    @Mock
    private LRUCache<String, List<WfStatusCountDTO>> localCache;
    @Mock
    private StorageService storageService;
    @Mock
    private Producer kafkaProducer;
    @Mock
    private ElasticsearchServiceManager eServiceManager;
    @Mock
    private NotificationTriggerService notificationTriggerService;
    @Mock
    private UserUtil userUtil;

    private AiAssessmentServiceImpl spyService;

    @BeforeEach
    void setUp() {
        AiAssessmentServiceImpl service = new AiAssessmentServiceImpl(wfStatusRepo, wfAuditRepo, mapper, configuration,
                userProfileWfService, requestServiceImpl, producer, cassandraOperation, accessTokenValidator,
                localCache, storageService, kafkaProducer, eServiceManager, notificationTriggerService, userUtil);
        spyService = Mockito.spy(service);
    }

    private WfRequest validWfRequest(String action) {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setUserId("user1");
        wfRequest.setServiceName(Constants.AI_ASSESSMENT_SERVICE_NAME);
        wfRequest.setState("SOME_STATE");
        wfRequest.setAction(action);
        wfRequest.setRootOrgId("root1");
        HashMap<String, Object> updateField = new HashMap<>();
        updateField.put("toValue", Map.of("status", "value"));
        wfRequest.setUpdateFieldValues(List.of(updateField));
        return wfRequest;
    }

    // ---- aiAssessmentWorkflowTransition ----

    @Test
    void testAiAssessmentWorkflowTransition_approve_pushesToTopic() {
        WfRequest wfRequest = validWfRequest(Constants.APPROVE);
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("APPROVER"));
        when(configuration.getAiAssessmentApproveRejectRoles()).thenReturn(List.of("APPROVER"));
        when(configuration.getAiAssessmentTopic()).thenReturn("ai-topic");
        Response stubResponse = new Response();
        doReturn(stubResponse).when(spyService).workflowTransition(anyString(), anyString(), any(WfRequest.class));

        Response result = spyService.aiAssessmentWorkflowTransition(wfRequest, "token");

        assertSame(stubResponse, result);
        verify(producer).push(eq("ai-topic"), any());
    }

    @Test
    void testAiAssessmentWorkflowTransition_initiate_doesNotPushToTopic() {
        WfRequest wfRequest = validWfRequest(Constants.INITIATE);
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("INITIATOR"));
        when(configuration.getAiAssessmentInitiateRoles()).thenReturn(List.of("INITIATOR"));
        doReturn(new Response()).when(spyService).workflowTransition(anyString(), anyString(), any(WfRequest.class));

        spyService.aiAssessmentWorkflowTransition(wfRequest, "token");

        verify(producer, never()).push(anyString(), any());
    }

    @Test
    void testAiAssessmentWorkflowTransition_unauthorizedInitiate_throws() {
        WfRequest wfRequest = validWfRequest(Constants.INITIATE);
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("OTHER_ROLE"));
        when(configuration.getAiAssessmentInitiateRoles()).thenReturn(List.of("INITIATOR"));

        assertThrows(BadRequestException.class,
                () -> spyService.aiAssessmentWorkflowTransition(wfRequest, "token"));
    }

    @Test
    void testAiAssessmentWorkflowTransition_unauthorizedApprove_throws() {
        WfRequest wfRequest = validWfRequest(Constants.APPROVE);
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("OTHER_ROLE"));
        when(configuration.getAiAssessmentApproveRejectRoles()).thenReturn(List.of("APPROVER"));

        assertThrows(BadRequestException.class,
                () -> spyService.aiAssessmentWorkflowTransition(wfRequest, "token"));
    }

    @Test
    void testAiAssessmentWorkflowTransition_invalidAction_throws() {
        WfRequest wfRequest = validWfRequest("SOME_OTHER_ACTION");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("ANY"));

        assertThrows(BadRequestException.class,
                () -> spyService.aiAssessmentWorkflowTransition(wfRequest, "token"));
    }

    @Test
    void testAiAssessmentWorkflowTransition_missingUserId_throws() {
        WfRequest wfRequest = validWfRequest(Constants.APPROVE);
        wfRequest.setUserId("");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("APPROVER"));
        when(configuration.getAiAssessmentApproveRejectRoles()).thenReturn(List.of("APPROVER"));

        assertThrows(InvalidDataInputException.class,
                () -> spyService.aiAssessmentWorkflowTransition(wfRequest, "token"));
    }

    @Test
    void testAiAssessmentWorkflowTransition_missingServiceName_throws() {
        WfRequest wfRequest = validWfRequest(Constants.APPROVE);
        wfRequest.setServiceName("");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("APPROVER"));
        when(configuration.getAiAssessmentApproveRejectRoles()).thenReturn(List.of("APPROVER"));

        assertThrows(InvalidDataInputException.class,
                () -> spyService.aiAssessmentWorkflowTransition(wfRequest, "token"));
    }

    @Test
    void testAiAssessmentWorkflowTransition_wrongServiceName_throws() {
        WfRequest wfRequest = validWfRequest(Constants.APPROVE);
        wfRequest.setServiceName("some_other_service");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("APPROVER"));
        when(configuration.getAiAssessmentApproveRejectRoles()).thenReturn(List.of("APPROVER"));

        assertThrows(InvalidDataInputException.class,
                () -> spyService.aiAssessmentWorkflowTransition(wfRequest, "token"));
    }

    @Test
    void testAiAssessmentWorkflowTransition_missingState_throws() {
        WfRequest wfRequest = validWfRequest(Constants.APPROVE);
        wfRequest.setState("");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("APPROVER"));
        when(configuration.getAiAssessmentApproveRejectRoles()).thenReturn(List.of("APPROVER"));

        assertThrows(InvalidDataInputException.class,
                () -> spyService.aiAssessmentWorkflowTransition(wfRequest, "token"));
    }

    @Test
    void testAiAssessmentWorkflowTransition_missingRootOrgId_throws() {
        WfRequest wfRequest = validWfRequest(Constants.APPROVE);
        wfRequest.setRootOrgId("");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("APPROVER"));
        when(configuration.getAiAssessmentApproveRejectRoles()).thenReturn(List.of("APPROVER"));

        assertThrows(InvalidDataInputException.class,
                () -> spyService.aiAssessmentWorkflowTransition(wfRequest, "token"));
    }

    @Test
    void testAiAssessmentWorkflowTransition_missingUpdateFieldValues_throws() {
        WfRequest wfRequest = validWfRequest(Constants.APPROVE);
        wfRequest.setUpdateFieldValues(new ArrayList<>());
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("APPROVER"));
        when(configuration.getAiAssessmentApproveRejectRoles()).thenReturn(List.of("APPROVER"));

        assertThrows(InvalidDataInputException.class,
                () -> spyService.aiAssessmentWorkflowTransition(wfRequest, "token"));
    }

    @Test
    void testAiAssessmentWorkflowTransition_missingAction_throws() {
        WfRequest wfRequest = validWfRequest(Constants.APPROVE);
        wfRequest.setAction("");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("APPROVER"));
        when(configuration.getAiAssessmentApproveRejectRoles()).thenReturn(List.of("APPROVER"));

        // action is checked for role-authorization BEFORE the mandatory-field validation,
        // and an empty action fails the role switch's default branch first.
        assertThrows(BadRequestException.class,
                () -> spyService.aiAssessmentWorkflowTransition(wfRequest, "token"));
    }

    // ---- fetchAiAssessement ----

    @Test
    void testFetchAiAssessement_invalidToken_throws() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("badToken")).thenReturn("");

        SearchCriteria criteria = new SearchCriteria();
        assertThrows(BadRequestException.class,
                () -> spyService.fetchAiAssessement("badToken", criteria));
    }

    @Test
    void testFetchAiAssessement_unauthorizedRole_throws() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("RANDOM_ROLE"));
        when(configuration.getAiAssessmentApproveRejectRoles()).thenReturn(List.of("SPV_PUBLISHER"));

        SearchCriteria criteria = new SearchCriteria();
        assertThrows(BadRequestException.class,
                () -> spyService.fetchAiAssessement("token", criteria));
    }

    @Test
    void testFetchAiAssessement_authorized_delegatesToParent() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("SPV_PUBLISHER"));
        when(configuration.getAiAssessmentApproveRejectRoles()).thenReturn(List.of("SPV_PUBLISHER"));
        Response stubResponse = new Response();
        SearchCriteria criteria = new SearchCriteria();
        doReturn(stubResponse).when(spyService).getAiAssessmentRequests(criteria);

        Response result = spyService.fetchAiAssessement("token", criteria);

        assertSame(stubResponse, result);
    }

    // ---- getAiAssessmentRequestByUserId ----

    @Test
    void testGetAiAssessmentRequestByUserId_invalidToken_wrapsInApplicationException() {
        when(configuration.getAiAssessmentInitiateRoles()).thenReturn(List.of("INITIATOR"));
        when(accessTokenValidator.fetchUserIdFromAccessToken("badToken")).thenReturn("");

        assertThrows(ApplicationException.class,
                () -> spyService.getAiAssessmentRequestByUserId("badToken"));
    }

    @Test
    void testGetAiAssessmentRequestByUserId_unauthorizedRole_wrapsInApplicationException() {
        when(configuration.getAiAssessmentInitiateRoles()).thenReturn(List.of("INITIATOR"));
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("OTHER"));

        assertThrows(ApplicationException.class,
                () -> spyService.getAiAssessmentRequestByUserId("token"));
    }

    @Test
    void testGetAiAssessmentRequestByUserId_noEntities_returnsEmptyResponse() {
        when(configuration.getAiAssessmentInitiateRoles()).thenReturn(List.of("INITIATOR"));
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("INITIATOR"));
        when(wfStatusRepo.findByUserIdAndServiceNameOrderByLastUpdatedOnDesc("user1", Constants.AI_ASSESSMENT_SERVICE_NAME))
                .thenReturn(new ArrayList<>());

        Response response = spyService.getAiAssessmentRequestByUserId("token");

        assertEquals(Constants.SUCCESSFUL, response.get(Constants.MESSAGE));
        assertEquals(0, response.get(Constants.COUNT));
        assertEquals(HttpStatus.OK, response.get(Constants.STATUS));
        assertTrue(((List<?>) response.get(Constants.DATA)).isEmpty());
    }

    @Test
    void testGetAiAssessmentRequestByUserId_withEntities_returnsEnrichedProfiles() {
        when(configuration.getAiAssessmentInitiateRoles()).thenReturn(List.of("INITIATOR"));
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("INITIATOR"));

        WfStatusEntity entity = new WfStatusEntity();
        entity.setApplicationId("app1");
        when(wfStatusRepo.findByUserIdAndServiceNameOrderByLastUpdatedOnDesc("user1", Constants.AI_ASSESSMENT_SERVICE_NAME))
                .thenReturn(List.of(entity));

        List<Map<String, Object>> enriched = List.of(Map.of("userId", "user1"));
        when(userProfileWfService.enrichUserData(anyMap(), isNull())).thenReturn(enriched);

        Response response = spyService.getAiAssessmentRequestByUserId("token");

        assertEquals(Constants.SUCCESSFUL, response.get(Constants.MESSAGE));
        assertEquals(1, response.get(Constants.COUNT));
        assertEquals(enriched, response.get(Constants.DATA));
    }

    @Test
    void testGetAiAssessmentRequestByUserId_repoThrows_wrapsInApplicationException() {
        when(configuration.getAiAssessmentInitiateRoles()).thenReturn(List.of("INITIATOR"));
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        when(accessTokenValidator.fetchUserRolesFromToken("token")).thenReturn(List.of("INITIATOR"));
        when(wfStatusRepo.findByUserIdAndServiceNameOrderByLastUpdatedOnDesc(anyString(), anyString()))
                .thenThrow(new RuntimeException("DB down"));

        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> spyService.getAiAssessmentRequestByUserId("token"));
        assertEquals("Error fetching AI Assessment request for user", ex.getMessage());
    }
}
