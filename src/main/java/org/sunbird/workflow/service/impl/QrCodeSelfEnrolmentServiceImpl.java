package org.sunbird.workflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.QrSelfEnrolRequest;
import org.sunbird.workflow.models.Response;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.service.ContentReadService;
import org.sunbird.workflow.service.QrCodeSelfEnrolmentService;
import org.sunbird.workflow.utils.AccessTokenValidator;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QrCodeSelfEnrolmentServiceImpl implements QrCodeSelfEnrolmentService {

    private static final Logger logger = LogManager.getLogger(QrCodeSelfEnrolmentServiceImpl.class);

    private final BPWorkFlowServiceImpl bpWorkFlowService;
    private final WfStatusRepo wfStatusRepo;
    private final Configuration configuration;
    private final AccessTokenValidator accessTokenValidator;
    private final ContentReadService contentReadService;
    private final ObjectMapper mapper;
    private final WorkflowAuditProcessingServiceImpl workflowAuditProcessingService;

    /**
     * Orchestrate QR Code self-enrolment workflow with direct approval
     *
     * @param rootOrg       - Root organization from header
     * @param org           - Organization from header
     * @param userAuthToken - JWT authentication token
     * @param qrRequest     - QR self-enrolment request with courseId and batchId
     * @return Response object with enrolment result (success 200 OK or error 400 BAD_REQUEST)
     */
    @Override
    public Response enrolQrCodeBPWorkFlow(String rootOrg, String org, String userAuthToken, QrSelfEnrolRequest qrRequest) {
        logger.info("QR Code enrolment initiated for course: {}, batch: {}",
            qrRequest != null ? qrRequest.getCourseId() : "null",
            qrRequest != null ? qrRequest.getBatchId() : "null");

        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userAuthToken);
        if (StringUtils.isEmpty(userId)) {
            logger.warn("QR enrolment failed: Invalid or expired access token provided");
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, Constants.INVALID_ACCESS_TOKEN_ERROR);
            response.put(Constants.STATUS, HttpStatus.UNAUTHORIZED);
            return response;
        }
        logger.debug("User ID extracted from token: {}", userId);

        // Validate QR request
        Response validationResponse = validateQrRequest(qrRequest, rootOrg, org);
        if (validationResponse != null) {
            return validationResponse;
        }

        String courseId = qrRequest.getCourseId();
        String batchId = qrRequest.getBatchId();

        // Course validation
        Map<String, Object> courseDetails = contentReadService.getServiceNameDetails(courseId);
        validationResponse = validateCourseExists(courseDetails, courseId);
        if (validationResponse != null) {
            return validationResponse;
        }

        validationResponse = validateSelfEnrolmentEnabled(courseDetails);
        if (validationResponse != null) {
            return validationResponse;
        }
        logger.debug("Course validation completed for courseId: {}", courseId);

        // Batch validation
        Map<String, Object> courseBatchDetails = bpWorkFlowService.getCurrentBatchAttributes(batchId, courseId);

        validationResponse = validateQrEnrollmentWindow(courseBatchDetails, batchId, courseId);
        if (validationResponse != null) {
            return validationResponse;
        }
        logger.debug("Batch validation completed for batchId: {}", batchId);

        // Enrollment rules validation
        validationResponse = validateUniqueBatchEnrollment(userId, courseId, batchId);
        if (validationResponse != null) {
            return validationResponse;
        }
        logger.debug("Single batch enrollment validation passed for userId: {}", userId);

        WfRequest wfRequest = buildQrEnrolRequest(userId, courseId, batchId, courseBatchDetails);

        int totalApprovedUserCount = bpWorkFlowService.getTotalApprovedUserCount(wfRequest);
        int totalUserEnrolCount = bpWorkFlowService.getTotalUserEnrolCountForBatch(wfRequest.getApplicationId());
        logger.debug("Batch capacity check: approved={}, total enrolled={}", totalApprovedUserCount, totalUserEnrolCount);

        boolean enrolAccess = bpWorkFlowService.validateBatchEnrolment(courseBatchDetails, totalApprovedUserCount, totalUserEnrolCount,
                Constants.BP_ENROLL_STATE);
        if (!enrolAccess) {
            logger.warn("QR enrolment failed: Batch capacity exceeded for batchId: {}", batchId);
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, configuration.getBatchFullMesg());
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }

        validationResponse = validateNoActiveWorkflow(userId, batchId);
        if (validationResponse != null) {
            return validationResponse;
        }
        logger.info("QR enrollment validation passed for userId: {}", userId);

        // QR Direct Approval: Save wf_status with APPROVED status directly
        WfStatusEntity wfStatusEntity = saveQrEnrollmentDirect(rootOrg, org, wfRequest);
        logger.info("QR enrolment record created with wfId: {}, userId: {}, courseId: {}",
                wfStatusEntity.getWfId(), userId, courseId);

        // Call enrollment logic directly (no Kafka, no workflow processing)
        bpWorkFlowService.updateEnrolmentDetails(wfRequest);
        logger.info("User enrolled successfully in blended program. UserId: {}, CourseId: {}, BatchId: {}",
                userId, courseId, batchId);

        // Create audit trail for compliance tracking
        try {
            workflowAuditProcessingService.createAudit(wfRequest);
            logger.debug("Audit trail created for QR enrollment wfId: {}", wfStatusEntity.getWfId());
        } catch (Exception e) {
            logger.error("Error creating audit trail for QR enrollment wfId: {}", wfStatusEntity.getWfId(), e);
        }

        // Build and return success response
        Response response = new Response();
        HashMap<String, Object> data = new HashMap<>();
        data.put(Constants.STATUS, Constants.APPROVED);
        data.put(Constants.WF_IDS_CONSTANT, wfStatusEntity.getWfId());
        response.put(Constants.MESSAGE, String.format(Constants.QR_ENROLLMENT_SUCCESS_MESSAGE));
        response.put(Constants.DATA, data);
        response.put(Constants.STATUS, HttpStatus.OK);
        logger.debug("QR enrolment response built successfully for wfId: {}", wfStatusEntity.getWfId());
        return response;
    }

    /**
     * Validate QR self-enrolment request - check required fields and headers
     *
     * @param qrRequest - QR self-enrolment request
     * @param rootOrg   - Root organization header
     * @param org       - Organization header
     * @return Response if validation fails, null if validation passes
     */
    private Response validateQrRequest(QrSelfEnrolRequest qrRequest, String rootOrg, String org) {
        logger.debug("Validating QR enrolment request headers and body");

        if (qrRequest == null) {
            logger.warn("QR enrolment request validation failed: Request body is null");
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, Constants.QR_REQUEST_NULL_ERROR);
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }

        if (StringUtils.isBlank(qrRequest.getCourseId())) {
            logger.warn("QR enrolment request validation failed: Course ID is missing");
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, Constants.COURSE_ID_REQUIRED_ERROR);
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }

        if (StringUtils.isBlank(qrRequest.getBatchId())) {
            logger.warn("QR enrolment request validation failed: Batch ID is missing");
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, Constants.BATCH_ID_REQUIRED_ERROR);
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }

        if (StringUtils.isBlank(rootOrg)) {
            logger.warn("QR enrolment request validation failed: Root Organization header is missing");
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, Constants.ROOT_ORG_REQUIRED_ERROR);
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }

        if (StringUtils.isBlank(org)) {
            logger.warn("QR enrolment request validation failed: Organization header is missing");
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, Constants.ORG_REQUIRED_ERROR);
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }

        logger.debug("QR request validation successful. CourseId: {}, BatchId: {}, RootOrg: {}, Org: {}",
                qrRequest.getCourseId(), qrRequest.getBatchId(), rootOrg, org);
        return null;
    }

    /**
     * Validate course exists and has required details
     *
     * @param courseDetails - Course details map
     * @param courseId      - Course ID
     * @return Response if validation fails, null if validation passes
     */
    private Response validateCourseExists(Map<String, Object> courseDetails, String courseId) {
        if (MapUtils.isEmpty(courseDetails)) {
            logger.warn("QR enrolment failed: Course not found for courseId: {}", courseId);
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, String.format(Constants.COURSE_NOT_FOUND_ERROR, courseId));
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }
        return null;
    }

    /**
     * Validate batch exists and QR Code enrollment is only allowed on batch start date
     *
     * @param courseBatchDetails - Batch attributes containing start date
     * @param batchId - Batch ID for error logging
     * @param courseId - Course ID for error logging
     * @return Response if validation fails, null if validation passes
     */
    private Response validateQrEnrollmentWindow(Map<String, Object> courseBatchDetails, String batchId, String courseId) {
        if (MapUtils.isEmpty(courseBatchDetails)) {
            logger.warn("QR enrolment failed: Batch not found for batchId: {}, courseId: {}", batchId, courseId);
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, String.format(Constants.BATCH_NOT_FOUND_ERROR, courseId, batchId));
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }

        Date enrollmentEndDate = (Date) courseBatchDetails.get(Constants.ENROLMENT_END_DATE);
        Date batchStartDate = (Date) courseBatchDetails.get(Constants.START_DATE);

        if (enrollmentEndDate == null || batchStartDate == null) {
            logger.warn("QR enrolment failed: Batch enrollment period dates are not available");
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, Constants.BATCH_START_DATE_UNAVAILABLE_ERROR);
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }

        LocalDate batchStartLocalDate = batchStartDate.toInstant()
                .atZone(ZoneId.of(configuration.getSunbirdTimeZone()))
                .toLocalDate();
        LocalDate currentLocalDate = LocalDate.now(ZoneId.of(configuration.getSunbirdTimeZone()));

        if (currentLocalDate.isAfter(batchStartLocalDate)) {
            logger.warn("QR enrolment failed: Current date is after batch start date. Batch starts on {}, Today is {}",
                batchStartLocalDate, currentLocalDate);
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, Constants.BATCH_ENROLLMENT_PERIOD_ENDED_ERROR);
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }
        return null;
    }

    /**
     * Validate user is not already enrolled in this batch or another active batch for the same course
     *
     * @param userId   - User ID
     * @param courseId - Course ID
     * @param batchId  - Batch ID
     * @return Response if validation fails, null if validation passes
     */
    private Response validateUniqueBatchEnrollment(String userId, String courseId, String batchId) {
        logger.debug("Validating single batch enrollment - userId: {}, courseId: {}, batchId: {}", userId, courseId, batchId);

        List<Map<String, Object>> activeEnrollments = bpWorkFlowService.getActiveEnrollmentForUserAndCourse(userId, courseId);
        if (CollectionUtils.isEmpty(activeEnrollments)) {
            logger.debug("No existing enrollments found for userId: {}, courseId: {}", userId, courseId);
            return null;
        }

        Map<String, Object> enrollment = activeEnrollments.get(0);
        String existingBatchId = (String) enrollment.get(Constants.BATCH_ID);

        if (batchId.equals(existingBatchId)) {
            logger.warn("QR enrolment failed: User already enrolled in same batch - userId: {}, batchId: {}", userId, batchId);
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, Constants.USER_ALREADY_ENROLLED_SAME_BATCH_ERROR);
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }

        logger.warn("QR enrolment failed: User already enrolled in different batch - userId: {}, currentBatchId: {}, newBatchId: {}",
                userId, existingBatchId, batchId);
        Response response = new Response();
        response.put(Constants.ERROR_MESSAGE,
            String.format(Constants.USER_ALREADY_ENROLLED_DIFFERENT_BATCH_ERROR, existingBatchId));
        response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
        return response;
    }

    /**
     * Validate no active workflow exists for user and batch
     *
     * @param userId  - User ID
     * @param batchId - Batch ID
     * @return Response if validation fails, null if validation passes
     */
    private Response validateNoActiveWorkflow(String userId, String batchId) {
        logger.debug("Checking for active workflows - userId: {}, batchId: {}", userId, batchId);

        List<WfStatusEntity> existingWorkflows = wfStatusRepo.findByServiceNameAndUserIdAndApplicationId(
                Constants.BLENDED_PROGRAM_SERVICE_NAME, userId, batchId
        );

        if (!CollectionUtils.isEmpty(existingWorkflows)) {
            for (WfStatusEntity workflow : existingWorkflows) {
                if (!isTerminalStatus(workflow.getCurrentStatus())) {
                    logger.warn("QR enrolment failed: Active workflow found for userId: {}, batchId: {}, status: {}",
                            userId, batchId, workflow.getCurrentStatus());
                    Response response = new Response();
                    response.put(Constants.ERROR_MESSAGE,
                        String.format(Constants.ACTIVE_WORKFLOW_EXISTS_ERROR, workflow.getCurrentStatus()));
                    response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
                    return response;
                } else {
                    logger.debug("Found terminal status workflow for userId: {}, status: {}", userId, workflow.getCurrentStatus());
                }
            }
        }
        logger.debug("No active workflows found for userId: {}, batchId: {}", userId, batchId);
        return null;
    }

    /**
     * Check if a workflow status is terminal (no further transitions possible)
     *
     * @param status - Workflow status
     * @return true if status is terminal
     */
    private boolean isTerminalStatus(String status) {
        return Constants.APPROVED.equalsIgnoreCase(status) ||
                Constants.REJECTED.equalsIgnoreCase(status) ||
                Constants.WITHDRAWN.equalsIgnoreCase(status) ||
                Constants.REMOVED.equalsIgnoreCase(status);
    }

    /**
     * Validate self-enrollment is enabled for the course
     *
     * @param courseDetails - Course details map
     * @return Response if validation fails, null if validation passes
     */
    private Response validateSelfEnrolmentEnabled(Map<String, Object> courseDetails) {
        if (MapUtils.isEmpty(courseDetails)) {
            logger.warn("QR enrolment failed: Course details are not available");
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, Constants.COURSE_DETAILS_UNAVAILABLE_ERROR);
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }

        Object selfEnrollment = courseDetails.get(Constants.SELF_ENROLLMENT);

        if (selfEnrollment == null || !Constants.YES.equalsIgnoreCase(String.valueOf(selfEnrollment))) {
            logger.warn("QR enrolment failed: Self-enrollment not enabled for course");
            Response response = new Response();
            response.put(Constants.ERROR_MESSAGE, Constants.SELF_ENROLLMENT_NOT_ENABLED_ERROR);
            response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
            return response;
        }
        return null;
    }

    /**
     * Build WfRequest for QR enrollment with direct approval
     *
     * @param userId                - User ID
     * @param courseId              - Course ID
     * @param batchId               - Batch ID
     * @param courseBatchDetails    - Batch details
     * @return WfRequest configured for QR enrollment
     */
    private WfRequest buildQrEnrolRequest(String userId, String courseId, String batchId,
                                          Map<String, Object> courseBatchDetails) {
        WfRequest wfRequest = new WfRequest();
        wfRequest.setUserId(userId);
        wfRequest.setCourseId(courseId);
        wfRequest.setApplicationId(batchId);
        wfRequest.setBatchName((String) courseBatchDetails.get(Constants.BATCH_NAME));
        wfRequest.setBatchStartDate((Date) courseBatchDetails.get(Constants.START_DATE));
        wfRequest.setServiceName(Constants.SELF_ENROLL_BY_QR);
        wfRequest.setState(Constants.APPROVED);
        wfRequest.setAction(Constants.INITIATE);
        wfRequest.setActorUserId(userId);

        List<HashMap<String, Object>> updateFieldValues = new ArrayList<>();
        wfRequest.setUpdateFieldValues(updateFieldValues);

        return wfRequest;
    }

    /**
     * Save QR enrollment directly with APPROVED status.
     * QR self-enrollment is not a workflow, only an audit/history record.
     *
     * @param rootOrg   - Root organization
     * @param org       - Organization
     * @param wfRequest - Workflow request containing enrollment details
     * @return WfStatusEntity - Saved workflow status entity
     */
    private WfStatusEntity saveQrEnrollmentDirect(String rootOrg, String org, WfRequest wfRequest) {
        WfStatusEntity applicationStatus = new WfStatusEntity();
        String wfId = UUID.randomUUID().toString();
        logger.debug("Creating QR enrollment record with wfId: {} for user: {}", wfId, wfRequest.getUserId());

        applicationStatus.setWfId(wfId);
        applicationStatus.setApplicationId(wfRequest.getApplicationId());
        applicationStatus.setUserId(wfRequest.getUserId());
        applicationStatus.setInWorkflow(false);
        applicationStatus.setActorUUID(wfRequest.getActorUserId());
        applicationStatus.setCreatedOn(new Date());
        applicationStatus.setCurrentStatus(Constants.APPROVED);
        applicationStatus.setLastUpdatedOn(new Date());
        applicationStatus.setOrg(org);
        applicationStatus.setRootOrg(rootOrg);
        applicationStatus.setServiceName(Constants.BLENDED_PROGRAM_SERVICE_NAME);

        try {
            applicationStatus.setUpdateFieldValues(mapper.writeValueAsString(wfRequest.getUpdateFieldValues()));
        } catch (JsonProcessingException e) {
            logger.error("Error serializing update field values for wfId: {}", wfId, e);
        }

        applicationStatus.setDeptName(wfRequest.getDeptName());
        applicationStatus.setComment(wfRequest.getComment());
        wfRequest.setWfId(wfId);

        wfStatusRepo.save(applicationStatus);
        logger.debug("QR enrollment record persisted to database with status: APPROVED, wfId: {}", wfId);

        return applicationStatus;
    }

}
