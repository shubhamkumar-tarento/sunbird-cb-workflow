package org.sunbird.workflow.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.service.*;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationProcessingServiceImplTest {

    @InjectMocks
    private ApplicationProcessingServiceImpl service;

    @Mock
    private UserProfileWfService userProfileWfService;
    @Mock
    private Configuration configuration;
    @Mock
    private RequestServiceImpl requestService;
    @Mock
    private WfStatusRepo wfStatusRepo;
    @Mock
    private UserRegistrationWfService userRegService;
    @Mock
    private BPWorkFlowService bpWorkFlowService;
    @Mock
    private DomainWhiteListWorkFlowService domainWhiteListWorkFlowService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testProcessWfApplicationRequest_UserProfile() {
        WfRequest request = new WfRequest();
        request.setServiceName(Constants.PROFILE_SERVICE_NAME);

        service.processWfApplicationRequest(request);

        verify(userProfileWfService).updateUserProfile(request);
    }

    @Test
    void testProcessWfApplicationRequest_UserProfileFlag() {
        WfRequest request = new WfRequest();
        request.setServiceName(Constants.USER_PROFILE_FLAG_SERVICE);

        service.processWfApplicationRequest(request);

        verify(userProfileWfService).updateUserProfile(request);
    }

    @Test
    void testProcessWfApplicationRequest_UserRegistration() {
        WfRequest request = new WfRequest();
        request.setServiceName(Constants.USER_REGISTRATION_SERVICE_NAME);

        service.processWfApplicationRequest(request);

        verify(userRegService).processMessage(request);
    }

    @Test
    void testProcessWfApplicationRequest_Domain() {
        WfRequest request = new WfRequest();
        request.setServiceName(Constants.DOMAIN);

        service.processWfApplicationRequest(request);

        verify(domainWhiteListWorkFlowService).processDomainRequest(request);
        verify(bpWorkFlowService, never()).processWFRequest(request);
    }

    @Test
    void testProcessWfApplicationRequest_BlendedProgram() {
        WfRequest request = new WfRequest();
        request.setServiceName(Constants.BLENDED_PROGRAM_SERVICE_NAME);

        service.processWfApplicationRequest(request);

        verify(bpWorkFlowService).processWFRequest(request);
    }

    @Test
    void testProcessWfApplicationRequest_OneStepMdoApproval() {
        WfRequest request = new WfRequest();
        request.setServiceName(Constants.ONE_STEP_MDO_APPROVAL);

        service.processWfApplicationRequest(request);

        verify(bpWorkFlowService).processWFRequest(request);
    }

    @Test
    void testProcessWfApplicationRequest_TwoStepMdoAndPcApproval() {
        WfRequest request = new WfRequest();
        request.setServiceName(Constants.TWO_STEP_MDO_AND_PC_APPROVAL);

        service.processWfApplicationRequest(request);

        verify(bpWorkFlowService).processWFRequest(request);
    }

    @Test
    void testProcessWfApplicationRequest_TwoStepPcAndMdoApproval() {
        WfRequest request = new WfRequest();
        request.setServiceName(Constants.TWO_STEP_PC_AND_MDO_APPROVAL);

        service.processWfApplicationRequest(request);

        verify(bpWorkFlowService).processWFRequest(request);
    }

    @Test
    void testProcessWfApplicationRequest_DefaultCase() {
        WfRequest request = new WfRequest();
        request.setServiceName("UNKNOWN");

        service.processWfApplicationRequest(request);

        // nothing should happen
        verifyNoInteractions(userProfileWfService, userRegService, bpWorkFlowService, domainWhiteListWorkFlowService);
    }

    @Test
    void testUpdateDepartmentToPortalDBs_ApprovedProfile() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);

        HashMap<String, Object> fromValue = new HashMap<>();
        fromValue.put("name", "oldDept");

        HashMap<String, Object> toValue = new HashMap<>();
        toValue.put("name", "newDept");

        HashMap<String, Object> updateMap = new HashMap<>();
        updateMap.put("fieldKey", "professionalDetails");
        updateMap.put("fromValue", fromValue);
        updateMap.put("toValue", toValue);

        wfRequest.setUpdateFieldValues(List.of(updateMap));

        WfStatusEntity entity = new WfStatusEntity();
        entity.setCurrentStatus(Constants.APPROVED_STATE);
        entity.setRootOrg("rootOrg");
        entity.setOrg("org");

        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(entity);

        service.updateDepartmentToPortalDBs(wfRequest);

        verify(requestService).fetchResultUsingPost(any(), any(), eq(Map.class), any());
    }

    @Test
    void testUpdateDepartmentToPortalDBs_NonApproved() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);

        WfStatusEntity entity = new WfStatusEntity();
        entity.setCurrentStatus("REJECTED");

        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(entity);

        service.updateDepartmentToPortalDBs(wfRequest);

        verifyNoInteractions(requestService);
    }

    @Test
    void testUpdateDepartmentToPortalDBs_EmptyUpdateFields() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);

        HashMap<String, Object> updateMap = new HashMap<>();
        updateMap.put("fieldKey", null); // empty fieldKey
        wfRequest.setUpdateFieldValues(List.of(updateMap));

        WfStatusEntity entity = new WfStatusEntity();
        entity.setCurrentStatus(Constants.APPROVED_STATE);

        when(wfStatusRepo.findByApplicationIdAndWfId("appId", "wfId")).thenReturn(entity);

        service.updateDepartmentToPortalDBs(wfRequest);

        verifyNoInteractions(requestService);
    }

    @Test
    void testUpdateUserDepartment_Success() {
        when(configuration.getLexCoreServiceHost()).thenReturn("http://localhost");
        when(configuration.getDepartmentUpdatePath()).thenReturn("/updateDept");

        HashMap<String, Object> deptInfo = new HashMap<>();
        deptInfo.put("userId", "u1");

        service.updateUserDepartment("rootOrg", "org", "wid", deptInfo);

        verify(requestService).fetchResultUsingPost(any(), eq(deptInfo), eq(Map.class), any());
    }

    @Test
    void testUpdateUserDepartment_Exception() {
        when(configuration.getLexCoreServiceHost()).thenReturn("http://localhost");
        when(configuration.getDepartmentUpdatePath()).thenReturn("/updateDept");

        doThrow(new RuntimeException("fail")).when(requestService)
                .fetchResultUsingPost(any(), any(), eq(Map.class), any());

        HashMap<String, Object> deptInfo = new HashMap<>();
        deptInfo.put("userId", "u1");

        assertDoesNotThrow(() ->
                service.updateUserDepartment("rootOrg", "org", "wid", deptInfo)
        );

        verify(requestService).fetchResultUsingPost(any(), any(), eq(Map.class), any());
    }
}
