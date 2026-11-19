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
    void sendNotificationToMdoAdmin_shouldSendMailToMDOForOrgChange() {
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
    void sendNotificationToMdoAdmin_shouldSendNotificationToMdoAdminForNonOrgChange() {

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

    private WfRequest blendedProgramWfRequest() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setServiceName(Constants.BLENDED_PROGRAM_SERVICE_NAME);
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setCourseName("Course1");
        wfRequest.setBatchName("Batch1");
        wfRequest.setBatchStartDate(new Date());
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>()));

        when(configuration.getLearnerForwardedMailBody()).thenReturn("body #course_name #batch_name #batch_start_date #role");

        Map<String, Object> recipientInfo = new HashMap<>();
        recipientInfo.put(Constants.FIRST_NAME, "Ajay");
        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", recipientInfo);
        when(userProfileWfService.getUsersResult(any())).thenReturn(usersObj);

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(true);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        return wfRequest;
    }

    private WfStatusEntity blendedProgramWfStatusEntity(String currentStatus) {
        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(currentStatus);
        wfStatusEntity.setApplicationId("appId");
        wfStatusEntity.setServiceName(Constants.BLENDED_PROGRAM_SERVICE_NAME);
        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        return wfStatusEntity;
    }

    @Test
    void sendNotification_shouldBuildBlendedProgramMailContent_forPcApproval() {
        WfRequest wfRequest = blendedProgramWfRequest();
        blendedProgramWfStatusEntity(Constants.SEND_FOR_PC_APPROVAL);

        notificationService.sendNotification(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotification_shouldBuildBlendedProgramMailContent_forMdoApproval() {
        WfRequest wfRequest = blendedProgramWfRequest();
        blendedProgramWfStatusEntity(Constants.SEND_FOR_MDO_APPROVAL);

        notificationService.sendNotification(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotification_shouldBuildBlendedProgramMailContent_forApproved() {
        WfRequest wfRequest = blendedProgramWfRequest();
        blendedProgramWfStatusEntity(Constants.APPROVED);
        when(configuration.getApprovedMailBody()).thenReturn("approved #batch_name #course_name #batch_start_date");

        notificationService.sendNotification(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotification_shouldBuildBlendedProgramMailContent_forRejected() {
        WfRequest wfRequest = blendedProgramWfRequest();
        blendedProgramWfStatusEntity(Constants.REJECTED);
        when(configuration.getRejectedOrRemovedMailBody()).thenReturn("rejected #batch_name #course_name #action #batch_start_date");

        notificationService.sendNotification(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotification_shouldBuildBlendedProgramMailContent_forDefaultStatus() {
        WfRequest wfRequest = blendedProgramWfRequest();
        blendedProgramWfStatusEntity("SOME_OTHER_STATUS");

        notificationService.sendNotification(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotification_shouldReturnEmptyMailContent_whenNoUpdatedFieldValue() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setUpdateFieldValues(Collections.emptyList());

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus("APPROVED");
        wfStatusEntity.setApplicationId("appId");
        wfStatusEntity.setServiceName(Constants.PROFILE_SERVICE_NAME);

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(true);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        Map<String, Object> recipientInfo = new HashMap<>();
        recipientInfo.put(Constants.FIRST_NAME, "Ajay");
        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", recipientInfo);
        when(userProfileWfService.getUsersResult(any())).thenReturn(usersObj);

        notificationService.sendNotification(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotification_shouldAppendCommentSuffix_whenCommentPresent() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setServiceName(Constants.PROFILE_SERVICE_NAME);
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setComment("Needs additional review");

        HashMap<String, Object> updateFieldValues = new HashMap<>();
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

        Map<String, Object> recipientInfo = new HashMap<>();
        recipientInfo.put(Constants.FIRST_NAME, "Ajay");
        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", recipientInfo);
        when(userProfileWfService.getUsersResult(any())).thenReturn(usersObj);

        notificationService.sendNotification(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendEmailNotification_shouldReturnEarly_whenNotificationDisabled() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus("APPROVED");

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(false);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        notificationService.sendEmailNotification(wfRequest);

        verify(requestService, never()).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendEmailNotification_shouldAppendCommentSuffix_whenCommentPresent() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setComment("Please clarify");

        HashMap<String, Object> updateFieldValues = new HashMap<>();
        updateFieldValues.put("email", "ajay@example.com");
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        updateFieldValues.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(updateFieldValues));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus("APPROVED");
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
    void sendNotificationToMdoAdmin_shouldSkipFieldsWithoutToValue() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setAction("SOME_ACTION");
        wfRequest.setUserId("userId");

        HashMap<String, Object> noToValueField = new HashMap<>();
        noToValueField.put("firstName", "Ajay");
        HashMap<String, Object> withToValueField = new HashMap<>();
        HashMap<String, String> toValue = new HashMap<>();
        toValue.put("domain", "example.com");
        withToValueField.put("toValue", toValue);
        wfRequest.setUpdateFieldValues(List.of(withToValueField, noToValueField));

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
        when(userProfileWfService.getUsersResult(any())).thenReturn(usersObj);
        when(configuration.getMdoEmailTemplate()).thenReturn("template");
        when(configuration.getDomainHost()).thenReturn("http://domain");
        when(configuration.getMdoBaseUrl()).thenReturn("/mdo/{id}");
        when(configuration.getSenderMail()).thenReturn("sender@example.com");

        notificationService.sendNotificationToMdoAdmin(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotificationToMdoAdmin_shouldReturnEarly_whenNotificationDisabledOrTerminalState() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setAction("SOME_ACTION");
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(Map.of("toValue", Map.of("domain", "example.com")))));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus("PENDING");
        wfStatusEntity.setRootOrg("rootOrg");
        wfStatusEntity.setOrg("org");

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(false);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        notificationService.sendNotificationToMdoAdmin(wfRequest);

        verify(userProfileWfService, never()).getMdoAdminAndPCDetails(any(), any());
    }

    @Test
    void sendNotificationToMdoAdmin_shouldReturnEarly_whenNoMdoAdminEmails() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setAction("SOME_ACTION");
        wfRequest.setUpdateFieldValues(List.of(new HashMap<>(Map.of("toValue", Map.of("domain", "example.com")))));

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus("PENDING");
        wfStatusEntity.setRootOrg("rootOrg");
        wfStatusEntity.setOrg("org");

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(true);
        wfStatus.setState("PENDING");

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);
        when(userProfileWfService.getMdoAdminAndPCDetails(any(), any())).thenReturn(Collections.emptyList());

        notificationService.sendNotificationToMdoAdmin(wfRequest);

        verify(userProfileWfService, never()).getUsersResult(any());
    }

    @Test
    void sendNotificationToMdoAdminAndPC_shouldSendNominationNoticeWithComment_whenNotificationDisabled() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setCourseId("courseId");
        wfRequest.setBatchName("Batch1");
        wfRequest.setCourseName("Course1");
        wfRequest.setBatchStartDate(new Date());
        wfRequest.setState(Constants.SEND_FOR_PC_APPROVAL);
        wfRequest.setComment("Please review soon");

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.SEND_FOR_PC_APPROVAL);
        wfStatusEntity.setUserId("userId");
        wfStatusEntity.setRootOrg("rootOrg");
        wfStatusEntity.setOrg("org");
        wfStatusEntity.setAdditionalProperties("isNominatedByMdo:true");

        WfStatus wfStatus = new WfStatus();
        wfStatus.setNotificationEnable(false);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(workflowservice.getWorkflowStates(any(), any(), any(), any())).thenReturn(wfStatus);

        Map<String, Object> recipientInfo = new HashMap<>();
        recipientInfo.put(Constants.FIRST_NAME, "Ajay");
        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", recipientInfo);
        when(userProfileWfService.getUsersResult(any())).thenReturn(usersObj);
        when(userProfileWfService.getMdoAdminAndPCDetails(any(), any())).thenReturn(List.of("mdo@example.com"));
        when(configuration.getNominationRequestMailBody()).thenReturn("nomination #username #batch_name #blended_programme_name #batch_start_date");
        when(configuration.getApprovalRequetMailBody()).thenReturn("approval #username #batch_name #blended_programme_name #batch_start_date");
        when(configuration.getRequestForwardedMailBody()).thenReturn("forwarded #username #batch_name #blended_programme_name #batch_start_date");

        notificationService.sendNotificationToMdoAdminAndPC(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendAiAssessmentNotification_shouldNotifySpvWhenPending() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setRootOrgId("rootOrgId");

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.PENDING);
        wfStatusEntity.setCreatedOn(new Date());
        wfStatusEntity.setDeptName("Finance");

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(configuration.getAiAssessmentSpvAdminEmail()).thenReturn(List.of("spv@example.com"));

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.FIRST_NAME, "Ajay");
        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", userInfo);
        when(userProfileWfService.getUsersResult(any())).thenReturn(usersObj);

        when(configuration.getCbpPortalHost()).thenReturn("http://cbp");
        when(configuration.getAiAssessmentApprovalLink()).thenReturn("/approve");
        when(configuration.getAiAssessmentSpvEmailBody()).thenReturn("body #userName #organization #submittedOn #approvalLink #applicationName");
        when(configuration.getAiAssessmentSpvEmailSubject()).thenReturn("SPV Subject");

        notificationService.sendAiAssessmentNotification(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendAiAssessmentNotification_shouldSkipSpvNotificationWhenNoSpvEmails() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setRootOrgId("rootOrgId");

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.PENDING);

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);
        when(configuration.getAiAssessmentSpvAdminEmail()).thenReturn(Collections.emptyList());

        notificationService.sendAiAssessmentNotification(wfRequest);

        verify(userProfileWfService, never()).getUsersResult(any());
    }

    @Test
    void sendAiAssessmentNotification_shouldNotifyUserWhenApproved() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setActorUserId("approverId");

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.APPROVED);
        wfStatusEntity.setLastUpdatedOn(new Date());

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.FIRST_NAME, "Ajay");
        userInfo.put(Constants.EMAIL, "ajay@example.com");
        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", userInfo);

        Map<String, Object> approverInfo = new HashMap<>();
        approverInfo.put(Constants.FIRST_NAME, "Manager");
        Map<String, Object> approverObj = new HashMap<>();
        approverObj.put("approverId", approverInfo);

        when(userProfileWfService.getUsersResult(Collections.singleton("userId"))).thenReturn(usersObj);
        when(userProfileWfService.getUsersResult(Collections.singleton("approverId"))).thenReturn(approverObj);

        when(configuration.getAiAssessmentApprovedEmailSubject()).thenReturn("Approved Subject");
        when(configuration.getAiAssessmentApprovedEmailBody()).thenReturn("body #userName #approverName #actionDate #applicationName");

        notificationService.sendAiAssessmentNotification(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendAiAssessmentNotification_shouldNotifyUserWhenRejectedWithComment() {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setApplicationId("appId");
        wfRequest.setWfId("wfId");
        wfRequest.setUserId("userId");
        wfRequest.setActorUserId("approverId");
        wfRequest.setComment("Not sufficient evidence");

        WfStatusEntity wfStatusEntity = new WfStatusEntity();
        wfStatusEntity.setCurrentStatus(Constants.REJECTED);
        wfStatusEntity.setLastUpdatedOn(new Date());

        when(wfStatusRepo.findByApplicationIdAndWfId(any(), any())).thenReturn(wfStatusEntity);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.FIRST_NAME, "Ajay");
        userInfo.put(Constants.EMAIL, "ajay@example.com");
        Map<String, Object> usersObj = new HashMap<>();
        usersObj.put("userId", userInfo);
        when(userProfileWfService.getUsersResult(any())).thenReturn(usersObj);

        when(configuration.getAiAssessmentRejectedEmailSubject()).thenReturn("Rejected Subject");
        when(configuration.getAiAssessmentRejectedEmailBody()).thenReturn("body #userName #approverName #actionDate #rejectionReason #applicationName");

        notificationService.sendAiAssessmentNotification(wfRequest);

        verify(requestService, atLeast(0)).fetchResultUsingPost(any(), any(), any(), any());
    }

    @Test
    void sendNotificationToMdoLeader_shouldLogErrorWhenModeratorsNull() {
        assertDoesNotThrow(() -> notificationService.sendNotificationToMdoLeader(
                List.of("mdo@example.com"), "Community", "2024-06-01", null));
    }
}