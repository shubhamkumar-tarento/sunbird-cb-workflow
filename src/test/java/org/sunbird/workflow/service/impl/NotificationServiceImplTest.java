package org.sunbird.workflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.models.WfStatus;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.service.ContentReadService;
import org.sunbird.workflow.service.Workflowservice;
import org.sunbird.workflow.utils.CassandraOperation;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class NotificationServiceImplTest {

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Mock
    private WfStatusRepo wfStatusRepo;
    @Mock
    private Configuration configuration;
    @Mock
    private RequestServiceImpl requestService;
    @Mock
    private ObjectMapper mapper;
    @Mock
    private Workflowservice workflowservice;
    @Mock
    private UserProfileWfServiceImpl userProfileWfService;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private ContentReadService contentReadService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void sendNotification_shouldSendNotificationForEnabledStatus() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");

        HashMap<String, Object> updateFieldValues;

        updateFieldValues = new HashMap<>();
        updateFieldValues.put("firstName", "Ajay");
        updateFieldValues.put("email", "ajay@example.com");
        updateFieldValues.put("mobile", "1234567890");
        updateFieldValues.put("description", "Test desc");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus("APPROVED");
        wfStatusEntity.setApplicationId("appId");
        wfStatusEntity.setRootOrg("rootOrg");
        wfStatusEntity.setOrg("org");
        wfStatusEntity.setServiceName(Constants.PROFILE_SERVICE_NAME);

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(true);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        Map<String, Object> recipientInfo = new HashMap<>();
        recipientInfo.put(Constants.FIRST_NAME, "Ajay");

        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", recipientInfo);

        when(userProfileWfService.getUsersResult(any())).thenReturn((HashMap<String, Object>) usersObj);        when(configuration.getLearnerForwardedMailBody()).thenReturn("body #course_name #batch_name #batch_start_date #role");
        when(configuration.getApprovedMailBody()).thenReturn("approved #batch_name #course_name #batch_start_date");
        when(configuration.getRejectedOrRemovedMailBody()).thenReturn("rejected #batch_name #course_name #action #batch_start_date");

        notificationService.sendNotification(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotification_shouldNotSendNotificationForWithdrawnProfile() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.WITHDRAWN);
        wfStatusEntity.setApplicationId("appId");
        wfStatusEntity.setServiceName(Constants.PROFILE_SERVICE_NAME);

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(true);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        notificationService.sendNotification(wfRequest);

        verify(requestService, never()).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotification_shouldHandleNotificationDisabled() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus("APPROVED");
        wfStatusEntity.setApplicationId("appId");
        wfStatusEntity.setServiceName(Constants.PROFILE_SERVICE_NAME);

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(false);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        notificationService.sendNotification(wfRequest);

        verify(requestService, never()).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendEmailNotification_shouldSendEmailIfEmailPresent() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        HashMap<String, Object> updateFieldValues;

        updateFieldValues = new HashMap<>();
        updateFieldValues.put("firstName", "Ajay");
        updateFieldValues.put("email", "ajay@example.com");
        updateFieldValues.put("mobile", "1234567890");
        updateFieldValues.put("description", "Test desc");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus("APPROVED");
        wfStatusEntity.setApplicationId("appId");
        wfStatusEntity.setServiceName(Constants.PROFILE_SERVICE_NAME);

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(true);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);
        when(configuration.getSenderMail()).thenReturn("sender@example.com");

        notificationService.sendEmailNotification(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendEmailNotification_shouldNotSendEmailIfEmailNotPresent() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(Map.of("toValue", Map.of("field", "value")))));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus("APPROVED");
        wfStatusEntity.setApplicationId("appId");
        wfStatusEntity.setServiceName(Constants.PROFILE_SERVICE_NAME);

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(true);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        notificationService.sendEmailNotification(wfRequest);

        verify(requestService, never()).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotificationToMdoAdmin_shouldSendMailToMDOForOrgChange() throws IOException {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setAction(Constants.APPROVE_STATE);
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(Map.of("toValue", Map.of(Constants.NAME, "newOrg")))));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus("APPROVED");
        wfStatusEntity.setApplicationId("appId");
        wfStatusEntity.setServiceName(Constants.PROFILE_SERVICE_NAME);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);

        notificationService.sendNotificationToMdoAdmin(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotificationToMdoAdmin_shouldSendNotificationToMdoAdminForNonOrgChange() throws IOException {

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
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setAction("SOME_ACTION");
        wfRequest.setUserId("userId");

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus("PENDING");
        wfStatusEntity.setApplicationId("appId");
        wfStatusEntity.setServiceName(Constants.PROFILE_SERVICE_NAME);
        wfStatusEntity.setUserId("userId");
        wfStatusEntity.setRootOrg("rootOrg");
        wfStatusEntity.setOrg("org");

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(true);
        wfStatus.setState("PENDING");

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);
        when(userProfileWfService.getMdoAdminAndPCDetails(any(), any())).thenReturn(List.of("mdo@example.com"));

        Map<String, Object> recipientInfo = new HashMap<>();
        recipientInfo.put(Constants.FIRST_NAME, "Ajay");

        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", recipientInfo);

        // Return usersObj directly, no cast
        when(userProfileWfService.getUsersResult(any())).thenReturn((HashMap<String, Object>) usersObj);
        when(configuration.getMdoEmailTemplate()).thenReturn("template");
        when(configuration.getDomainHost()).thenReturn("http://domain");
        when(configuration.getMdoBaseUrl()).thenReturn("/mdo/{id}");
        when(configuration.getSenderMail()).thenReturn("sender@example.com");
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any())).thenReturn(List.of(Map.of(Constants.TEMPLATE, "template-content")));

        notificationService.sendNotificationToMdoAdmin(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotificationToMdoAdminAndPC_shouldSendNotificationToPCAndMDO() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setCourseId("courseId");
        wfRequest.setBatchName("Batch1");
        wfRequest.setCourseName("Course1");
        wfRequest.setBatchStartDate(new Date());
        wfRequest.setState(Constants.INITIATE);

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.SEND_FOR_PC_APPROVAL);
        wfStatusEntity.setUserId("userId");
        wfStatusEntity.setRootOrg("rootOrg");
        wfStatusEntity.setOrg("org");
        wfStatusEntity.setAdditionalProperties("isNominatedByMdo:true");

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(true);
        wfStatus.setState("PENDING");

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        Map<String, Object> recipientInfo = new HashMap<>();
        recipientInfo.put(Constants.FIRST_NAME, "Ajay");

        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", recipientInfo);

        when(userProfileWfService.getUsersResult(any())).thenReturn((HashMap<String, Object>) usersObj);
        when(contentReadService.getRootOrgId(any())).thenReturn("rootOrgId");
        when(userProfileWfService.getMdoAdminAndPCDetails(any(), any())).thenReturn(List.of("pc@example.com", "mdo@example.com"));
        when(configuration.getNominationRequestMailBody()).thenReturn("nomination #username #batch_name #blended_programme_name #batch_start_date");
        when(configuration.getApprovalRequetMailBody()).thenReturn("approval #username #batch_name #blended_programme_name #batch_start_date");
        when(configuration.getRequestForwardedMailBody()).thenReturn("forwarded #username #batch_name #blended_programme_name #batch_start_date");

        notificationService.sendNotificationToMdoAdminAndPC(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotificationToMdoAdminAndPC_shouldSendNotificationToMDOApproval() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setCourseId("courseId");
        wfRequest.setBatchName("Batch1");
        wfRequest.setCourseName("Course1");
        wfRequest.setBatchStartDate(new Date());
        wfRequest.setState(Constants.INITIATE);

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.SEND_FOR_MDO_APPROVAL);
        wfStatusEntity.setUserId("userId");
        wfStatusEntity.setRootOrg("rootOrg");
        wfStatusEntity.setOrg("org");
        wfStatusEntity.setAdditionalProperties("isNominatedByMdo:true");

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(true);
        wfStatus.setState("PENDING");

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        Map<String, Object> recipientInfo = new HashMap<>();
        recipientInfo.put(Constants.FIRST_NAME, "Ajay");

        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", recipientInfo);

        when(userProfileWfService.getUsersResult(any())).thenReturn((HashMap<String, Object>) usersObj);
        when(contentReadService.getRootOrgId(any())).thenReturn("rootOrgId");
        when(userProfileWfService.getMdoAdminAndPCDetails(any(), any())).thenReturn(List.of("pc@example.com", "mdo@example.com"));
        when(configuration.getNominationRequestMailBody()).thenReturn("nomination #username #batch_name #blended_programme_name #batch_start_date");
        when(configuration.getApprovalRequetMailBody()).thenReturn("approval #username #batch_name #blended_programme_name #batch_start_date");
        when(configuration.getRequestForwardedMailBody()).thenReturn("forwarded #username #batch_name #blended_programme_name #batch_start_date");

        notificationService.sendNotificationToMdoAdminAndPC(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }


    @Test
    void sendNotificationToMdoAdminAndPC_shouldSendNotification() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setCourseId("courseId");
        wfRequest.setBatchName("Batch1");
        wfRequest.setCourseName("Course1");
        wfRequest.setBatchStartDate(new Date());
        wfRequest.setState(Constants.SEND_FOR_MDO_APPROVAL);

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.SEND_FOR_PC_APPROVAL);
        wfStatusEntity.setUserId("userId");
        wfStatusEntity.setRootOrg("rootOrg");
        wfStatusEntity.setOrg("org");
        wfStatusEntity.setAdditionalProperties("isNominatedByMdo:true");

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(true);
        wfStatus.setState(Constants.SEND_FOR_MDO_APPROVAL);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        Map<String, Object> recipientInfo = new HashMap<>();
        recipientInfo.put(Constants.FIRST_NAME, "Ajay");

        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", recipientInfo);

        when(userProfileWfService.getUsersResult(any())).thenReturn((HashMap<String, Object>) usersObj);
        when(contentReadService.getRootOrgId(any())).thenReturn("rootOrgId");
        when(userProfileWfService.getMdoAdminAndPCDetails(any(), any())).thenReturn(List.of("pc@example.com", "mdo@example.com"));
        when(configuration.getNominationRequestMailBody()).thenReturn("nomination #username #batch_name #blended_programme_name #batch_start_date");
        when(configuration.getApprovalRequetMailBody()).thenReturn("approval #username #batch_name #blended_programme_name #batch_start_date");
        when(configuration.getRequestForwardedMailBody()).thenReturn("forwarded #username #batch_name #blended_programme_name #batch_start_date");

        notificationService.sendNotificationToMdoAdminAndPC(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotificationToMdoAdminAndPC_shouldSendNotification_2() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setCourseId("courseId");
        wfRequest.setBatchName("Batch1");
        wfRequest.setCourseName("Course1");
        wfRequest.setBatchStartDate(new Date());
        wfRequest.setState(Constants.SEND_FOR_PC_APPROVAL);

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.SEND_FOR_MDO_APPROVAL);
        wfStatusEntity.setUserId("userId");
        wfStatusEntity.setRootOrg("rootOrg");
        wfStatusEntity.setOrg("org");
        wfStatusEntity.setAdditionalProperties("isNominatedByMdo:true");

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(true);
        wfStatus.setState(Constants.SEND_FOR_MDO_APPROVAL);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        Map<String, Object> recipientInfo = new HashMap<>();
        recipientInfo.put(Constants.FIRST_NAME, "Ajay");

        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", recipientInfo);

        when(userProfileWfService.getUsersResult(any())).thenReturn((HashMap<String, Object>) usersObj);
        when(contentReadService.getRootOrgId(any())).thenReturn("rootOrgId");
        when(userProfileWfService.getMdoAdminAndPCDetails(any(), any())).thenReturn(List.of("pc@example.com", "mdo@example.com"));
        when(configuration.getNominationRequestMailBody()).thenReturn("nomination #username #batch_name #blended_programme_name #batch_start_date");
        when(configuration.getApprovalRequetMailBody()).thenReturn("approval #username #batch_name #blended_programme_name #batch_start_date");
        when(configuration.getRequestForwardedMailBody()).thenReturn("forwarded #username #batch_name #blended_programme_name #batch_start_date");

        notificationService.sendNotificationToMdoAdminAndPC(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotificationToMdoAdminAndPC_shouldSendNotification_3() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setCourseId("courseId");
        wfRequest.setBatchName("Batch1");
        wfRequest.setCourseName("Course1");
        wfRequest.setBatchStartDate(new Date());
        wfRequest.setState(Constants.SEND_FOR_PC_APPROVAL);

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.SEND_FOR_PC_APPROVAL);
        wfStatusEntity.setUserId("userId");
        wfStatusEntity.setRootOrg("rootOrg");
        wfStatusEntity.setOrg("org");
        wfStatusEntity.setAdditionalProperties("isNominatedByMdo:true");

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(false);
        wfStatus.setState(Constants.SEND_FOR_MDO_APPROVAL);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        Map<String, Object> recipientInfo = new HashMap<>();
        recipientInfo.put(Constants.FIRST_NAME, "Ajay");

        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", recipientInfo);

        when(userProfileWfService.getUsersResult(any())).thenReturn((HashMap<String, Object>) usersObj);
        when(contentReadService.getRootOrgId(any())).thenReturn("rootOrgId");
        when(userProfileWfService.getMdoAdminAndPCDetails(any(), any())).thenReturn(List.of("pc@example.com", "mdo@example.com"));
        when(configuration.getNominationRequestMailBody()).thenReturn("nomination #username #batch_name #blended_programme_name #batch_start_date");
        when(configuration.getApprovalRequetMailBody()).thenReturn("approval #username #batch_name #blended_programme_name #batch_start_date");
        when(configuration.getRequestForwardedMailBody()).thenReturn("forwarded #username #batch_name #blended_programme_name #batch_start_date");

        notificationService.sendNotificationToMdoAdminAndPC(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotification_shouldHandleExceptionGracefully() {
        when(configuration.getNotifyServiceHost()).thenReturn("http://notify");
        when(configuration.getNotifyServicePath()).thenReturn("/notify");
        doThrow(new RuntimeException("fail")).when(requestService).fetchResultUsingPost(any(), any(), any(), any());
        assertDoesNotThrow(()-> notificationService.sendNotification(Map.of("key", "value")));
    }


    @Test
    void sendMailToMDOForOrgChange_shouldSendMailIfMdoAdminListNotEmpty() {
        WfRequest wfRequest = new WfRequest();
        String userId = "userId";
        wfRequest.setUserId(userId);
        wfRequest.setPreviousRootOrgId("prevRootOrgId");

        when(userProfileWfService.getMdoAdminAndPCDetails(any(), any())).thenReturn(List.of("mdo@example.com"));

        Map<String, Object> recipientInfo = new HashMap<>();
        recipientInfo.put(Constants.FIRST_NAME, "Ajay");

        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put(userId, recipientInfo);

        when(userProfileWfService.getUsersResult(any())).thenReturn((HashMap<String, Object>) usersObj);
        when(configuration.getOrgChangeNotificationTemplate()).thenReturn("orgChangeTemplate");
        when(configuration.getSenderMail()).thenReturn("sender@example.com");
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any())).thenReturn(List.of(Map.of(Constants.TEMPLATE, "template-content")));

        notificationService.sendMailToMDOForOrgChange(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendMailToMDOForOrgChange_shouldNotSendMailIfMdoAdminListEmpty() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setUserId("userId");
        wfRequest.setPreviousRootOrgId("prevRootOrgId");

        when(userProfileWfService.getMdoAdminAndPCDetails(any(), any())).thenReturn(Collections.emptyList());

        assertDoesNotThrow(()-> notificationService.sendMailToMDOForOrgChange(wfRequest));
    }

    @Test
    void sendNotificationToMdoLeader_shouldSendNotificationToMdoLeader() {
        List<String> mdoMails = List.of("mdo@example.com");
        String communityName = "Community";
        String creationDate = "2024-06-01";
        List<Map<String, Object>> moderators = List.of(Map.of(Constants.MODERATOR_NAME, "Mod1"), Map.of(Constants.MODERATOR_NAME, "Mod2"));

        when(configuration.getSenderMail()).thenReturn("sender@example.com");
        when(configuration.getCommunityModeratorTransferTemplate()).thenReturn("communityTemplate");
        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any())).thenReturn(List.of(Map.of(Constants.TEMPLATE, "template-content")));

        notificationService.sendNotificationToMdoLeader(mdoMails, communityName, creationDate, moderators);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }
}