package org.sunbird.workflow.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.Response;
import org.sunbird.workflow.models.SearchCriteria;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.service.Workflowservice;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganisationWorkFlowServiceImplTest {

    @InjectMocks
    OrganisationWorkFlowServiceImpl service;

    @Mock
    Workflowservice workflowService;

    @Mock
    Configuration configuration;

    @Mock
    RequestServiceImpl requestServiceImpl;

    @Captor
    ArgumentCaptor<Map<String, Object>> requestCaptor;

    WfRequest wfRequest;

    @BeforeEach
    void setUp() {
        wfRequest = new WfRequest();
    }

    @Test
    void testCreateOrgWorkFlow_EmailExists() {
        Map<String, Object> field = Map.of(Constants.EMAIL, "email@test.com");
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(field)));

        Response response = service.createOrgWorkFlow("rootOrg", "org", wfRequest);

        assertEquals(Constants.EMAIL_EXIST_ERROR, response.get(Constants.ERROR_MESSAGE));
        assertEquals(HttpStatus.BAD_REQUEST, response.get(Constants.STATUS));
    }

    @Test
    void testCreateOrgWorkFlow_PhoneExists() {
        Map<String, Object> field = Map.of(Constants.PHONE, "9999999999");
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(field)));

        Response response = service.createOrgWorkFlow("rootOrg", "org", wfRequest);

        assertEquals(Constants.PHONE_NUMBER_EXIST_ERROR, response.get(Constants.ERROR_MESSAGE));
        assertEquals(HttpStatus.BAD_REQUEST, response.get(Constants.STATUS));
    }

    @Test
    void testCreateOrgWorkFlow_OrgNameExists() {
        Map<String, Object> toValueMap = Map.of(Constants.ORGANISATION_SERVICE_NAME, "orgName");
        Map<String, Object> field = Map.of(Constants.TO_VALUE, toValueMap);
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(field)));

        Response response = service.createOrgWorkFlow("rootOrg", "org", wfRequest);

        assertEquals(Constants.ORGANIZATION_EXIST_ERROR, response.get(Constants.ERROR_MESSAGE));
        assertEquals(HttpStatus.BAD_REQUEST, response.get(Constants.STATUS));
    }

    @Test
    void testCreateOrgWorkFlow_Success() {
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>()));

        Response wfResponse = new Response();
        when(workflowService.workflowTransition(anyString(), anyString(), any())).thenReturn(wfResponse);

        Response response = service.createOrgWorkFlow("rootOrg", "org", wfRequest);

        assertEquals(HttpStatus.OK, response.get(Constants.STATUS));
    }

    @Test
    void testUpdateOrgWorkFlow() {
        Response wfResponse = new Response();
        when(workflowService.workflowTransition(anyString(), anyString(), any())).thenReturn(wfResponse);

        Response response = service.updateOrgWorkFlow("rootOrg", "org", wfRequest);

        assertSame(wfResponse, response);
    }

    @Test
    void testReadOrgWFApplication() {
        Response wfResponse = new Response();
        when(workflowService.getWfApplication(anyString(), anyString(), anyString(), anyString())).thenReturn(wfResponse);

        Response response = service.readOrgWFApplication("rootOrg", "org", "wfId", "appId");

        assertSame(wfResponse, response);
    }

    @Test
    void testOrgSearch() {
        Response wfResponse = new Response();
        when(workflowService.applicationsSearch(anyString(), anyString(), any(), eq(Constants.ORG_SEARCH_ENABLED)))
                .thenReturn(wfResponse);

        Response response = service.orgSearch("rootOrg", "org", new SearchCriteria());

        assertSame(wfResponse, response);
    }

    @Test
    void testIsUserDetailExists_Found() {
        setupConfig();
        Map<String, Object> response = buildSearchResponse(1);

        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenReturn(response);

        boolean exists = service.isUserDetailExists("email", "test@test.com");

        assertTrue(exists);
    }

    @Test
    void testIsUserDetailExists_NotFound() {
        setupConfig();
        Map<String, Object> response = buildSearchResponse(0);

        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenReturn(response);

        boolean exists = service.isUserDetailExists("email", "test@test.com");

        assertFalse(exists);
    }

    @Test
    void testIsUserDetailExists_Exception() {
        setupConfig();
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenThrow(new RuntimeException("fail"));

        boolean exists = service.isUserDetailExists("email", "test@test.com");

        assertTrue(exists);
    }

    @Test
    void testIsOrgDetailExists_Found() {
        when(configuration.getLmsServiceHost()).thenReturn("http://lms/");
        when(configuration.getLmsOrgSearchEndPoint()).thenReturn("org/search");
        Map<String, Object> response = buildSearchResponse(1);

        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenReturn(response);

        boolean exists = service.isOrgDetailExists("orgName", "testOrg");

        assertTrue(exists);
    }

    @Test
    void testIsOrgDetailExists_NotFound() {
        when(configuration.getLmsServiceHost()).thenReturn("http://lms/");
        when(configuration.getLmsOrgSearchEndPoint()).thenReturn("org/search");
        Map<String, Object> response = buildSearchResponse(0);

        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenReturn(response);

        boolean exists = service.isOrgDetailExists("orgName", "testOrg");

        assertFalse(exists);
    }

    @Test
    void testIsOrgDetailExists_Exception() {
        when(configuration.getLmsServiceHost()).thenReturn("http://lms/");
        when(configuration.getLmsOrgSearchEndPoint()).thenReturn("org/search");
        when(requestServiceImpl.fetchResultUsingPost(any(), any(), eq(Map.class), any())).thenThrow(new RuntimeException("fail"));

        boolean exists = service.isOrgDetailExists("orgName", "testOrg");

        assertTrue(exists);
    }

    private void setupConfig() {
        when(configuration.getLmsServiceHost()).thenReturn("http://lms/");
        when(configuration.getLmsUserSearchEndPoint()).thenReturn("user/search");
    }

    private Map<String, Object> buildSearchResponse(int count) {
        Map<String, Object> innerResponse = new HashMap<>();
        innerResponse.put(Constants.COUNT, count);

        Map<String, Object> result = new HashMap<>();
        result.put(Constants.RESPONSE, innerResponse);

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, Constants.OK);
        response.put(Constants.RESULT, result);

        return response;
    }
}
