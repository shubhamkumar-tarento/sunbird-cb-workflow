package org.sunbird.workflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.QrSelfEnrolRequest;
import org.sunbird.workflow.models.Response;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.producer.Producer;
import org.sunbird.workflow.service.ContentReadService;
import org.sunbird.workflow.utils.AccessTokenValidator;
import org.sunbird.workflow.utils.CassandraOperation;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BPWorkFlowServiceQrEnrolIntegrationTest {

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private WfStatusRepo wfStatusRepo;

    @Mock
    private ContentReadService contentReadService;

    @Mock
    private Producer producer;

    @Mock
    private Configuration configuration;

    @Mock
    private RequestServiceImpl requestServiceImpl;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private WorkflowAuditProcessingServiceImpl workflowAuditProcessingService;

    @InjectMocks
    private BPWorkFlowServiceImpl bpWorkFlowService;

    private QrCodeSelfEnrolmentServiceImpl qrCodeSelfEnrolmentService;

    private String rootOrg;
    private String org;
    private String userId;
    private QrSelfEnrolRequest qrRequest;

    @BeforeEach
    void setUp() {
        rootOrg = "igot";
        org = "dopt";
        userId = "user123";
        qrRequest = new QrSelfEnrolRequest("course123", "batch123");

        lenient().when(configuration.getSunbirdTimeZone()).thenReturn("Asia/Kolkata");
        lenient().when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn(userId);
        lenient().when(configuration.getCourseServiceHost()).thenReturn("http://course-service:8080");
        lenient().when(configuration.getAdminEnrolEndPoint()).thenReturn("/api/v1/user/enroll");

        // Mock successful enrollment response
        Map<String, Object> enrollmentResponse = new HashMap<>();
        enrollmentResponse.put(Constants.RESPONSE_CODE, "OK");
        lenient().when(requestServiceImpl.fetchResultUsingPost(any(), any(), any(), any()))
                .thenReturn(enrollmentResponse);

        // Inject ObjectMapper instance
        ReflectionTestUtils.setField(bpWorkFlowService, "mapper", new ObjectMapper());

        // Setup QrCodeSelfEnrolmentServiceImpl with constructor injection
        qrCodeSelfEnrolmentService = new QrCodeSelfEnrolmentServiceImpl(
                bpWorkFlowService,
                wfStatusRepo,
                configuration,
                accessTokenValidator,
                contentReadService,
                new ObjectMapper(),
                workflowAuditProcessingService
        );
    }

    @Test
    void testCompleteQrEnrolmentFlow_HappyPath() {
        setupBatchDetails();
        setupCourseDetails();
        setupNoExistingWorkflow();

        Response response = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userId, qrRequest);

        assertSuccessResponse(response);
        verifyWfStatusSaved();
    }

    @Test
    void testQrEnrolmentFlow_WfStatusCreatedCorrectly() {
        setupBatchDetails();
        setupCourseDetails();
        setupNoExistingWorkflow();

        Response response = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userId, qrRequest);

        assertSuccessResponse(response);

        ArgumentCaptor<WfStatusEntity> captor = ArgumentCaptor.forClass(WfStatusEntity.class);
        verify(wfStatusRepo).save(captor.capture());

        WfStatusEntity savedEntity = captor.getValue();
        assertNotNull(savedEntity.getWfId());
        assertEquals(userId, savedEntity.getUserId());
        assertEquals("batch123", savedEntity.getApplicationId());
        assertEquals(Constants.APPROVED, savedEntity.getCurrentStatus());
        assertEquals(rootOrg, savedEntity.getRootOrg());
        assertEquals(org, savedEntity.getOrg());
        assertFalse(savedEntity.getInWorkflow());
        assertEquals(Constants.BLENDED_PROGRAM_SERVICE_NAME, savedEntity.getServiceName());
    }

    @Test
    void testQrEnrolmentFlow_MultipleEnrollmentAttempts_SecondFails() {
        setupBatchDetails();
        setupCourseDetails();

        // First attempt - no existing workflow
        lenient().when(wfStatusRepo.findByServiceNameAndUserIdAndApplicationId(anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        Response firstResponse = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userId, qrRequest);
        assertSuccessResponse(firstResponse);

        // Second attempt - workflow already exists
        WfStatusEntity activeWorkflow = new WfStatusEntity();
        activeWorkflow.setWfId("wf123");
        activeWorkflow.setCurrentStatus(Constants.SEND_FOR_PC_APPROVAL);
        activeWorkflow.setUserId(userId);

        reset(wfStatusRepo);
        when(wfStatusRepo.findByServiceNameAndUserIdAndApplicationId(anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<WfStatusEntity>() {{
                    add(activeWorkflow);
                }});

        Response response = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userId, qrRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.get(Constants.STATUS));
        assertTrue(response.get(Constants.ERROR_MESSAGE).toString().contains("Active workflow already exists"));
    }

    @Test
    void testQrEnrolmentFlow_DateValidation_SameDayEnrollmentAllowed() {
        try {
            Map<String, Object> batchAttributesMap = new HashMap<>();
            batchAttributesMap.put(Constants.CURRENT_BATCH_SIZE, "50");

            ObjectMapper mapper = new ObjectMapper();
            String batchAttributesJson = mapper.writeValueAsString(batchAttributesMap);

            Map<String, Object> todayBatchDetails = new HashMap<>();
            todayBatchDetails.put(Constants.BATCH_ATTRIBUTES, batchAttributesJson);
            todayBatchDetails.put(Constants.ENROLMENT_END_DATE, getTodayDate().toInstant());
            todayBatchDetails.put(Constants.START_DATE, getTodayPlusDays(30).toInstant());
            todayBatchDetails.put(Constants.NAME, "Test Batch");

            when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any()))
                    .thenReturn(new ArrayList<>(new ArrayList<Map<String, Object>>() {{
                        add(todayBatchDetails);
                    }}));
            when(contentReadService.getServiceNameDetails("course123"))
                    .thenReturn(createCourseDetails());
            when(wfStatusRepo.findByServiceNameAndUserIdAndApplicationId(anyString(), anyString(), anyString()))
                    .thenReturn(new ArrayList<>());

            Response response = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userId, qrRequest);

            assertSuccessResponse(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testQrEnrolmentFlow_DateValidation_NextDayEnrollmentFails() {
        try {
            Map<String, Object> batchAttributesMap = new HashMap<>();
            batchAttributesMap.put(Constants.CURRENT_BATCH_SIZE, "50");

            ObjectMapper mapper = new ObjectMapper();
            String batchAttributesJson = mapper.writeValueAsString(batchAttributesMap);

            Map<String, Object> tomorrowBatchDetails = new HashMap<>();
            tomorrowBatchDetails.put(Constants.BATCH_ATTRIBUTES, batchAttributesJson);
            tomorrowBatchDetails.put(Constants.ENROLMENT_END_DATE, getTodayPlusDays(-1).toInstant());
            tomorrowBatchDetails.put(Constants.START_DATE, getTodayPlusDays(-1).toInstant());
            tomorrowBatchDetails.put(Constants.NAME, "Test Batch");

            when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any()))
                    .thenReturn(new ArrayList<>(new ArrayList<Map<String, Object>>() {{
                        add(tomorrowBatchDetails);
                    }}));
            when(contentReadService.getServiceNameDetails("course123"))
                    .thenReturn(createCourseDetails());
            lenient().when(wfStatusRepo.findByServiceNameAndUserIdAndApplicationId(anyString(), anyString(), anyString()))
                    .thenReturn(new ArrayList<>());

            Response response = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userId, qrRequest);

            assertEquals(HttpStatus.BAD_REQUEST, response.get(Constants.STATUS));
            assertTrue(response.get(Constants.ERROR_MESSAGE).toString().contains("enrolment"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testQrEnrolmentFlow_BothRequestAndHeaderValidation() {
        // Test missing root org
        Response response1 = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow("", org, userId, qrRequest);
        assertEquals(HttpStatus.BAD_REQUEST, response1.get(Constants.STATUS));
        assertTrue(response1.get(Constants.ERROR_MESSAGE).toString().contains("Root Organization is required"));

        // Test missing org
        Response response2 = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, "", userId, qrRequest);
        assertEquals(HttpStatus.BAD_REQUEST, response2.get(Constants.STATUS));
        assertTrue(response2.get(Constants.ERROR_MESSAGE).toString().contains("Organization is required"));

        // Test missing course ID
        Response response3 = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userId, new QrSelfEnrolRequest("", "batch123"));
        assertEquals(HttpStatus.BAD_REQUEST, response3.get(Constants.STATUS));
        assertTrue(response3.get(Constants.ERROR_MESSAGE).toString().contains("Course ID is required"));
    }

    @Test
    void testQrEnrolmentFlow_AllTerminalStatusesIgnored() {
        String[] terminalStatuses = {Constants.APPROVED, Constants.REJECTED, Constants.WITHDRAWN, Constants.REMOVED};

        for (String status : terminalStatuses) {
            setupBatchDetails();
            setupCourseDetails();

            WfStatusEntity terminalWorkflow = new WfStatusEntity();
            terminalWorkflow.setCurrentStatus(status);
            terminalWorkflow.setUserId(userId);

            when(wfStatusRepo.findByServiceNameAndUserIdAndApplicationId(anyString(), anyString(), anyString()))
                    .thenReturn(new ArrayList<WfStatusEntity>() {{
                        add(terminalWorkflow);
                    }});

            Response response = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userId, qrRequest);

            assertEquals(HttpStatus.OK, response.get(Constants.STATUS),
                    "Terminal status '" + status + "' should be ignored");
        }
    }

    @Test
    void testQrEnrolmentFlow_InvalidAccessToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");

        Response response = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, "invalid-token", qrRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.get(Constants.STATUS));
        assertTrue(response.get(Constants.ERROR_MESSAGE).toString().contains("Invalid access token"));
    }

    @Test
    void testQrEnrolmentFlow_CourseNotFound() {
        setupCourseDetails();

        when(contentReadService.getServiceNameDetails("nonexistent-course"))
                .thenReturn(null);

        Response response = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userId,
                        new QrSelfEnrolRequest("nonexistent-course", "batch123"));

        assertEquals(HttpStatus.BAD_REQUEST, response.get(Constants.STATUS));
        assertTrue(response.get(Constants.ERROR_MESSAGE).toString().contains("Course not found"));
    }

    @Test
    void testQrEnrolmentFlow_SelfEnrollmentNotEnabled() {
        Map<String, Object> courseDetailsNoSelfEnroll = new HashMap<>();
        courseDetailsNoSelfEnroll.put(Constants.SELF_ENROLLMENT, "No");

        when(contentReadService.getServiceNameDetails("course123"))
                .thenReturn(courseDetailsNoSelfEnroll);

        Response response = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userId, qrRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.get(Constants.STATUS));
        assertTrue(response.get(Constants.ERROR_MESSAGE).toString().contains("Self-enrolment is not enabled for this course"));
    }

    @Test
    void testQrEnrolmentFlow_BatchNotFound() {
        setupCourseDetails();

        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any()))
                .thenReturn(new ArrayList<>());

        Response response = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userId, qrRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.get(Constants.STATUS));
        assertTrue(response.get(Constants.ERROR_MESSAGE).toString().contains("Batch not found"));
    }

    @Test
    void testQrEnrolmentFlow_BatchEnrollmentUpdatesSuccessfully() {
        setupBatchDetails();
        setupCourseDetails();
        setupNoExistingWorkflow();

        Response response = qrCodeSelfEnrolmentService.enrolQrCodeBPWorkFlow(rootOrg, org, userId, qrRequest);

        assertSuccessResponse(response);
        verify(requestServiceImpl, atLeastOnce()).fetchResultUsingPost(any(), any(), any(), any());
    }

    private void setupBatchDetails() {
        try {
            Map<String, Object> batchAttributesMap = new HashMap<>();
            batchAttributesMap.put(Constants.CURRENT_BATCH_SIZE, "50");

            ObjectMapper mapper = new ObjectMapper();
            String batchAttributesJson = mapper.writeValueAsString(batchAttributesMap);

            Map<String, Object> batchDetails = new HashMap<>();
            batchDetails.put(Constants.BATCH_ATTRIBUTES, batchAttributesJson);
            batchDetails.put(Constants.ENROLMENT_END_DATE, getTodayDate().toInstant());
            batchDetails.put(Constants.START_DATE, getTodayPlusDays(30).toInstant());
            batchDetails.put(Constants.NAME, "Test Batch");

            lenient().when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), any(), any()))
                    .thenReturn(new ArrayList<>(new ArrayList<Map<String, Object>>() {{
                        add(batchDetails);
                    }}));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setupCourseDetails() {
        lenient().when(contentReadService.getServiceNameDetails("course123"))
                .thenReturn(createCourseDetails());
    }

    private void setupNoExistingWorkflow() {
        lenient().when(wfStatusRepo.findByServiceNameAndUserIdAndApplicationId(anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
    }

    private Map<String, Object> createCourseDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("wfApprovalType", Constants.ONE_STEP_PC_APPROVAL);
        details.put(Constants.SELF_ENROLLMENT, "Yes");
        return details;
    }

    private void assertSuccessResponse(Response response) {
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.get(Constants.STATUS));
        assertNotNull(response.get(Constants.DATA));
    }

    private void verifyWfStatusSaved() {
        verify(wfStatusRepo, atLeastOnce()).save(any(WfStatusEntity.class));
    }

    private Date getTodayDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        return Date.from(today.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
    }

    private Date getTodayPlusDays(int days) {
        LocalDate future = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(days);
        return Date.from(future.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
    }
}
