package org.sunbird.workflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.*;
import org.sunbird.workflow.postgres.entity.*;
import org.sunbird.workflow.postgres.repo.*;
import org.sunbird.workflow.service.*;
import org.sunbird.workflow.utils.CassandraOperation;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainWhiteListWorkFlowServiceImplTest {

    @InjectMocks
    private DomainWhiteListWorkFlowServiceImpl service;

    @Mock
    private Workflowservice workflowService;
    @Mock
    private Configuration configuration;
    @Mock
    private WfStatusRepo wfStatusRepo;
    @Mock
    private UserProfileWfService userProfileWfService;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private WfDomainLookupRepo wfDomainLookupRepo;
    @Mock
    private WfDomainUserInfoRepo wfDomainUserInfoRepo;
    @Mock
    private ObjectMapper mapper;

    @Test
    void testCreateDomainWorkFlow_validNewDomain() throws Exception {
        when(configuration.getDomainValidationRegex()).thenReturn(".*");
        String rootOrg = "root";
        String org = "org";
        WfRequest wfRequest;
        HashMap<String, Object> updateFieldValues;

        wfRequest = new WfRequest();
        updateFieldValues = new HashMap<>();
        updateFieldValues.put("firstName", "Ajay");
        updateFieldValues.put("email", "ajay@example.com");
        updateFieldValues.put("mobile", "1234567890");
        updateFieldValues.put("description", "Test desc");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        when(wfDomainUserInfoRepo.findByDomainNameAndEmailAndMobile(any(), any(), any()))
                .thenReturn(List.of());
        when(wfDomainLookupRepo.findByDomainName(any())).thenReturn(List.of());
        when(wfDomainUserInfoRepo.countByDomainName(any())).thenReturn(1L);

        Response wfResponse = new Response();
        wfResponse.put(Constants.STATUS, HttpStatus.OK);
        wfResponse.put(Constants.DATA, Map.of(Constants.WF_IDS_CONSTANT, List.of("wf-id")));
        when(workflowService.workflowTransition(any(), any(), any())).thenReturn(wfResponse);

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        when(wfStatusRepo.findByWfId(any())).thenReturn(wfStatusEntity);
        when(wfDomainLookupRepo.save(any())).thenReturn(new WfDomainLookup());
        when(wfStatusRepo.save(any())).thenReturn(wfStatusEntity);
        when(mapper.writeValueAsString(any())).thenReturn("{}");

        Response response = service.createDomainWorkFlow(rootOrg, org, wfRequest);

        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.STATUS));
    }
    @Test
    void testCreateDomainWorkFlow_validNewDomain_1() throws Exception {
        when(configuration.getDomainValidationRegex()).thenReturn(".*");
        String rootOrg = "root";
        String org = "org";
        WfRequest wfRequest;
        HashMap<String, Object> updateFieldValues;

        wfRequest = new WfRequest();
        updateFieldValues = new HashMap<>();
        updateFieldValues.put("firstName", "Ajay");
        updateFieldValues.put("email", "ajay@example.com");
        updateFieldValues.put("mobile", "1234567890");
        updateFieldValues.put("description", "Test desc");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        when(wfDomainUserInfoRepo.findByDomainNameAndEmailAndMobile(any(), any(), any()))
                .thenReturn(List.of());
        WfDomainLookup lookup = new WfDomainLookup();
        lookup.setWfId("wf123");
        when(wfDomainLookupRepo.findByDomainName(any()))
                .thenReturn(List.of(lookup));
    when(wfDomainUserInfoRepo.countByDomainName(any())).thenReturn(1L);

        Response wfResponse = new Response();
        wfResponse.put(Constants.STATUS, HttpStatus.OK);
        wfResponse.put(Constants.DATA, Map.of(Constants.WF_IDS_CONSTANT, List.of("wf-id")));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        when(wfStatusRepo.findByWfId(any())).thenReturn(wfStatusEntity);
        when(wfStatusRepo.save(any())).thenReturn(wfStatusEntity);
        when(mapper.writeValueAsString(any())).thenReturn("{}");

        Response response = service.createDomainWorkFlow(rootOrg, org, wfRequest);

        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.STATUS));
    }

    @Test
    void testCreateDomainWorkFlow_invalidDomain() {
        when(configuration.getDomainValidationRegex()).thenReturn("^invalid$");
        WfRequest wfRequest;
        HashMap<String, Object> updateFieldValues;

        wfRequest = new WfRequest();
        updateFieldValues = new HashMap<>();
        updateFieldValues.put("firstName", "Ajay");
        updateFieldValues.put("email", "ajay@example.com");
        updateFieldValues.put("mobile", "1234567890");
        updateFieldValues.put("description", "Test desc");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));


        Response response = service.createDomainWorkFlow("root", "org", wfRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResult().get(Constants.STATUS));
    }

    @Test
    void testCreateDomainWorkFlow_approvedDomain() {

        when(configuration.getDomainValidationRegex()).thenReturn(".*");

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any()))
                .thenReturn(List.of(Map.of()));

        WfRequest wfRequest;
        HashMap<String, Object> updateFieldValues;

        wfRequest = new WfRequest();
        updateFieldValues = new HashMap<>();
        updateFieldValues.put("firstName", "Ajay");
        updateFieldValues.put("email", "ajay@example.com");
        updateFieldValues.put("mobile", "1234567890");
        updateFieldValues.put("description", "Test desc");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        Response response = service.createDomainWorkFlow("root", "org", wfRequest);

        assertEquals(HttpStatus.OK, response.getResult().get(Constants.STATUS));
    }

    @Test
    void testProcessDomainRequest_newApprovedDomain() {
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        WfRequest wfRequest;
        HashMap<String, Object> updateFieldValues;

        wfRequest = new WfRequest();
        updateFieldValues = new HashMap<>();
        updateFieldValues.put("firstName", "Ajay");
        updateFieldValues.put("email", "ajay@example.com");
        updateFieldValues.put("mobile", "1234567890");
        updateFieldValues.put("description", "Test desc");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));
        wfRequest.setAction(Constants.APPROVE_STATE);


        service.processDomainRequest(wfRequest);

        verify(cassandraOperation).insertRecord(any(), any(), any());
    }

    @Test
    void testDomainSearch() {
        when(configuration.getDefaultLimit()).thenReturn(10);
        when(configuration.getDefaultOffset()).thenReturn(0);
        when(configuration.getMaxLimit()).thenReturn(50);
        SearchCriteria criteria = new SearchCriteria();
        criteria.setServiceName("svc");
        criteria.setApplicationStatus("status");
        criteria.setDeptName("dept");
        criteria.setApplicationIds(List.of("appId"));

        WfStatusEntity entity = new WfStatusEntity();
        entity.setApplicationId("appId");

        when(wfStatusRepo.findByServiceNameAndCurrentStatusAndDeptNameAndApplicationIdIn(
                any(), any(), any(), any())).thenReturn(List.of(entity));

        when(userProfileWfService.enrichUserData(any(), any()))
                .thenReturn(List.of(Map.of()));

        Response response = service.domainSearch("root", "org", criteria);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResult().get(Constants.STATUS));
    }

    @Test
    void testCreateDomainWorkFlow_noUpdateFieldValues_returnsBadRequest() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setUpdateFieldValues(List.of());

        Response response = service.createDomainWorkFlow("root", "org", wfRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResult().get(Constants.STATUS));
        assertEquals("Update field values are missing", response.getResult().get(Constants.MESSAGE));
    }

    @Test
    void testCreateDomainWorkFlow_emptyDomainValue_returnsBadRequest() {
        when(configuration.getDomainValidationRegex()).thenReturn(".+");
        WfRequest wfRequest = new WfRequest();
        HashMap<String, Object> updateFieldValues = new HashMap<>();
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        Response response = service.createDomainWorkFlow("root", "org", wfRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResult().get(Constants.STATUS));
    }

    @Test
    void testCreateDomainWorkFlow_existingUserInfo_rejectedLookup_returnsRejectedMsg() {
        when(configuration.getDomainValidationRegex()).thenReturn(".*");
        WfRequest wfRequest = new WfRequest();
        HashMap<String, Object> updateFieldValues = new HashMap<>();
        updateFieldValues.put("firstName", "Ajay");
        updateFieldValues.put("email", "ajay@example.com");
        updateFieldValues.put("mobile", "1234567890");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        WfDomainUserInfo existingUserInfo = new WfDomainUserInfo();
        when(wfDomainUserInfoRepo.findByDomainNameAndEmailAndMobile(any(), any(), any()))
                .thenReturn(List.of(existingUserInfo));

        WfDomainLookup lookup = new WfDomainLookup();
        lookup.setWfId("wf-existing");
        when(wfDomainLookupRepo.findByDomainName(any())).thenReturn(List.of(lookup));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.REJECTED);
        when(wfStatusRepo.findByWfId("wf-existing")).thenReturn(wfStatusEntity);

        Response response = service.createDomainWorkFlow("root", "org", wfRequest);

        assertEquals(HttpStatus.OK, response.getResult().get(Constants.STATUS));
        assertEquals(Constants.DOMAIN_REQUEST_REJECTED, response.getResult().get(Constants.MSG_CODE));
    }

    @Test
    void testCreateDomainWorkFlow_existingUserInfo_nonRejectedLookup_returnsAlreadyRaisedMsg() {
        when(configuration.getDomainValidationRegex()).thenReturn(".*");
        WfRequest wfRequest = new WfRequest();
        HashMap<String, Object> updateFieldValues = new HashMap<>();
        updateFieldValues.put("firstName", "Ajay");
        updateFieldValues.put("email", "ajay@example.com");
        updateFieldValues.put("mobile", "1234567890");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        WfDomainUserInfo existingUserInfo = new WfDomainUserInfo();
        when(wfDomainUserInfoRepo.findByDomainNameAndEmailAndMobile(any(), any(), any()))
                .thenReturn(List.of(existingUserInfo));

        WfDomainLookup lookup = new WfDomainLookup();
        lookup.setWfId("wf-existing2");
        when(wfDomainLookupRepo.findByDomainName(any())).thenReturn(List.of(lookup));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.SEND_FOR_APPROVAL);
        when(wfStatusRepo.findByWfId("wf-existing2")).thenReturn(wfStatusEntity);

        Response response = service.createDomainWorkFlow("root", "org", wfRequest);

        assertEquals(HttpStatus.ACCEPTED, response.getResult().get(Constants.STATUS));
        assertEquals(Constants.DOMAIN_REQUEST_ALREADY_RAISED, response.getResult().get(Constants.MSG_CODE));
    }

    @Test
    void testCreateDomainWorkFlow_existingUserInfo_noDomainLookup_noOpResponse() {
        when(configuration.getDomainValidationRegex()).thenReturn(".*");
        WfRequest wfRequest = new WfRequest();
        HashMap<String, Object> updateFieldValues = new HashMap<>();
        updateFieldValues.put("firstName", "Ajay");
        updateFieldValues.put("email", "ajay@example.com");
        updateFieldValues.put("mobile", "1234567890");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        WfDomainUserInfo existingUserInfo = new WfDomainUserInfo();
        when(wfDomainUserInfoRepo.findByDomainNameAndEmailAndMobile(any(), any(), any()))
                .thenReturn(List.of(existingUserInfo));
        when(wfDomainLookupRepo.findByDomainName(any())).thenReturn(List.of());

        Response response = service.createDomainWorkFlow("root", "org", wfRequest);

        assertNotNull(response);
        assertFalse(response.getResult().containsKey(Constants.STATUS));
    }

    @Test
    void testCreateDomainWorkFlow_newRequest_rejectedExistingLookup_updatesAndTransitions() throws Exception {
        when(configuration.getDomainValidationRegex()).thenReturn(".*");
        WfRequest wfRequest = new WfRequest();
        HashMap<String, Object> updateFieldValues = new HashMap<>();
        updateFieldValues.put("firstName", "Ajay");
        updateFieldValues.put("email", "ajay@example.com");
        updateFieldValues.put("mobile", "1234567890");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        when(wfDomainUserInfoRepo.findByDomainNameAndEmailAndMobile(any(), any(), any())).thenReturn(List.of());
        WfDomainLookup lookup = new WfDomainLookup();
        lookup.setWfId("wf-rejected");
        when(wfDomainLookupRepo.findByDomainName(any())).thenReturn(List.of(lookup));
        when(wfDomainUserInfoRepo.countByDomainName(any())).thenReturn(1L);

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.REJECTED);
        when(wfStatusRepo.findByWfId("wf-rejected")).thenReturn(wfStatusEntity);

        Response wfResponse = new Response();
        wfResponse.put(Constants.STATUS, HttpStatus.OK);
        wfResponse.put(Constants.DATA, Map.of(Constants.WF_IDS_CONSTANT, List.of("wf-id")));
        when(workflowService.workflowTransition(any(), any(), any())).thenReturn(wfResponse);
        when(wfStatusRepo.findByWfId("wf-id")).thenReturn(wfStatusEntity);
        when(wfDomainLookupRepo.save(any())).thenReturn(lookup);
        when(wfStatusRepo.save(any())).thenReturn(wfStatusEntity);
        when(mapper.writeValueAsString(any())).thenReturn("{}");

        Response response = service.createDomainWorkFlow("root", "org", wfRequest);

        assertEquals(Constants.DOMAIN_REQUEST_CREATED, response.getResult().get(Constants.MSG_CODE));
    }

    @Test
    void testCreateDomainWorkFlow_transitionNotOk_skipsDomainLookupUpdate() {
        when(configuration.getDomainValidationRegex()).thenReturn(".*");
        WfRequest wfRequest = new WfRequest();
        HashMap<String, Object> updateFieldValues = new HashMap<>();
        updateFieldValues.put("firstName", "Ajay");
        updateFieldValues.put("email", "ajay@example.com");
        updateFieldValues.put("mobile", "1234567890");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        when(wfDomainUserInfoRepo.findByDomainNameAndEmailAndMobile(any(), any(), any())).thenReturn(List.of());
        when(wfDomainLookupRepo.findByDomainName(any())).thenReturn(List.of());
        when(wfDomainUserInfoRepo.countByDomainName(any())).thenReturn(1L);

        Response wfResponse = new Response();
        wfResponse.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
        when(workflowService.workflowTransition(any(), any(), any())).thenReturn(wfResponse);

        Response response = service.createDomainWorkFlow("root", "org", wfRequest);

        assertNull(response.getResult().get(Constants.MSG_CODE));
        verify(wfDomainLookupRepo, never()).save(any());
    }

    @Test
    void testCreateDomainWorkFlow_exceptionDuringProcessing_returnsInternalServerError() {
        WfRequest wfRequest = new WfRequest();
        HashMap<String, Object> updateFieldValues = new HashMap<>();
        // "toValue" intentionally omitted -> NPE when the code calls toValue.get("domain")
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        Response response = service.createDomainWorkFlow("root", "org", wfRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResult().get(Constants.STATUS));
    }

    @Test
    void testUpdateDomainWorkFlow_delegatesToWorkflowService() {
        Response wfResponse = new Response();
        when(workflowService.workflowTransition(anyString(), anyString(), any())).thenReturn(wfResponse);

        Response response = service.updateDomainWorkFlow("root", "org", new WfRequest());

        assertSame(wfResponse, response);
    }

    @Test
    void testReadDomainWFApplication_delegatesToWorkflowService() {
        Response wfResponse = new Response();
        when(workflowService.getWfApplication(anyString(), anyString(), anyString(), anyString())).thenReturn(wfResponse);

        Response response = service.readDomainWFApplication("root", "org", "wfId", "appId");

        assertSame(wfResponse, response);
    }

    @Test
    void testDomainSearch_emptyCriteria_throwsBadRequestException() {
        SearchCriteria criteria = new SearchCriteria();

        org.sunbird.workflow.exception.BadRequestException ex = assertThrows(
                org.sunbird.workflow.exception.BadRequestException.class,
                () -> service.domainSearch("root", "org", criteria));

        assertEquals(Constants.SEARCH_CRITERIA_VALIDATION, ex.getMessage());
    }

    @Test
    void testDomainSearch_limitAboveDefault_cappedToDefaultLimit() {
        when(configuration.getDefaultLimit()).thenReturn(10);
        when(configuration.getDefaultOffset()).thenReturn(0);
        SearchCriteria criteria = new SearchCriteria();
        criteria.setServiceName("svc");
        criteria.setApplicationStatus("status");
        criteria.setDeptName("dept");
        criteria.setApplicationIds(null);
        criteria.setLimit(100);
        criteria.setOffset(5);

        Page<String> page = new PageImpl<>(List.of("appId"), PageRequest.of(5, 10), 1);
        when(wfStatusRepo.getListOfDistinctApplicationUsingDept(any(), any(), any(), any())).thenReturn(page);

        WfStatusEntity entity = new WfStatusEntity();
        entity.setApplicationId("appId");
        when(wfStatusRepo.findByServiceNameAndCurrentStatusAndDeptNameAndApplicationIdIn(any(), any(), any(), any()))
                .thenReturn(List.of(entity));
        when(userProfileWfService.enrichUserData(any(), any())).thenReturn(List.of(Map.of()));

        Response response = service.domainSearch("root", "org", criteria);

        assertEquals(HttpStatus.OK, response.getResult().get(Constants.STATUS));
    }

    @Test
    void testDomainSearch_limitBelowDefault_usesGivenLimit() {
        when(configuration.getDefaultLimit()).thenReturn(10);
        when(configuration.getDefaultOffset()).thenReturn(0);
        SearchCriteria criteria = new SearchCriteria();
        criteria.setServiceName("svc");
        criteria.setApplicationStatus("status");
        criteria.setDeptName("dept");
        criteria.setApplicationIds(null);
        criteria.setLimit(5);
        criteria.setOffset(null);

        Page<String> page = new PageImpl<>(List.of("appId"), PageRequest.of(0, 5), 1);
        when(wfStatusRepo.getListOfDistinctApplicationUsingDept(any(), any(), any(), any())).thenReturn(page);

        WfStatusEntity entity = new WfStatusEntity();
        entity.setApplicationId("appId");
        when(wfStatusRepo.findByServiceNameAndCurrentStatusAndDeptNameAndApplicationIdIn(any(), any(), any(), any()))
                .thenReturn(List.of(entity));
        when(userProfileWfService.enrichUserData(any(), any())).thenReturn(List.of(Map.of()));

        Response response = service.domainSearch("root", "org", criteria);

        assertEquals(HttpStatus.OK, response.getResult().get(Constants.STATUS));
    }

    @Test
    void testProcessDomainRequest_notApproveAction_noOp() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setAction("REJECT");
        wfRequest.setUpdateFieldValues(List.of());

        service.processDomainRequest(wfRequest);

        verify(cassandraOperation, never()).insertRecord(any(), any(), any());
    }

    @Test
    void testProcessDomainRequest_fieldWithoutToValue_noOp() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setAction(Constants.APPROVE_STATE);
        HashMap<String, Object> updateFieldValue = new HashMap<>();
        // no "toValue" key present
        wfRequest.setUpdateFieldValues(List.of(updateFieldValue));

        service.processDomainRequest(wfRequest);

        verify(cassandraOperation, never()).insertRecord(any(), any(), any());
    }

    @Test
    void testProcessDomainRequest_alreadyApprovedDomain_doesNotInsertAgain() {
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any()))
                .thenReturn(List.of(Map.of()));
        WfRequest wfRequest = new WfRequest();
        wfRequest.setAction(Constants.APPROVE_STATE);
        HashMap<String, Object> updateFieldValue = new HashMap<>();
        updateFieldValue.put(Constants.TO_VALUE, Map.of(Constants.DOMAIN, "already-approved.com"));
        wfRequest.setUpdateFieldValues(List.of(updateFieldValue));

        service.processDomainRequest(wfRequest);

        verify(cassandraOperation, never()).insertRecord(any(), any(), any());
    }

    @Test
    void testDomainSearch_withPaging() {
        // Arrange
        when(configuration.getDefaultLimit()).thenReturn(10);
        when(configuration.getDefaultOffset()).thenReturn(0);
        when(configuration.getMaxLimit()).thenReturn(50);

        SearchCriteria criteria = new SearchCriteria();
        criteria.setServiceName("svc");
        criteria.setApplicationStatus("status");
        criteria.setDeptName("dept");
        criteria.setApplicationIds(null);

        Page<String> page = new PageImpl<>(List.of("appId"), PageRequest.of(0, 10), 1);

        when(wfStatusRepo.getListOfDistinctApplicationUsingDept(any(), any(), any(), any()))
                .thenReturn(page);

        // Make sure WfStatusEntity has a non-null applicationId
        WfStatusEntity entity = new WfStatusEntity();
        entity.setApplicationId("appId");
        entity.setServiceName("svc");
        entity.setCurrentStatus("status");
        entity.setDeptName("dept");

        when(wfStatusRepo.findByServiceNameAndCurrentStatusAndDeptNameAndApplicationIdIn(
                any(), any(), any(), any()))
                .thenReturn(List.of(entity));

        when(userProfileWfService.enrichUserData(any(), any()))
                .thenReturn(List.of(Map.of()));

        // Act
        Response response = service.domainSearch("root", "org", criteria);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResult().get(Constants.STATUS));
    }

}
