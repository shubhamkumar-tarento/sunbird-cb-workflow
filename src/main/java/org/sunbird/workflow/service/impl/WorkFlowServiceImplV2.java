package org.sunbird.workflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.config.RedisCacheMgr;
import org.sunbird.workflow.exception.ApplicationException;
import org.sunbird.workflow.exception.BadRequestException;
import org.sunbird.workflow.exception.InvalidDataInputException;
import org.sunbird.workflow.models.*;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.producer.Producer;
import org.sunbird.workflow.service.WorkFlowServiceV2;
import org.sunbird.workflow.utils.AccessTokenValidator;
import org.sunbird.workflow.utils.CassandraOperation;
import org.sunbird.workflow.utils.NotificationTriggerService;
import org.sunbird.workflow.utils.WorkflowHelperUtil;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkFlowServiceImplV2 implements WorkFlowServiceV2 {


    Logger logger = LogManager.getLogger(WorkFlowServiceImplV2.class);

    private final WfStatusRepo wfStatusRepo;
    private final ObjectMapper mapper;
    private final CassandraOperation cassandraOperation;
    private final Configuration configuration;
    private final RequestServiceImpl requestServiceImpl;
    private final Producer producer;
    private final RedisCacheMgr redisCacheMgr;
    private final NotificationTriggerService notificationTriggerService;
    private final AccessTokenValidator accessTokenValidator;

    @Override
    public Response workflowTransition(String rootOrg, String org, Map<String, Object> requestBody, String userToken) {
        Response response = new Response();
        List<Object> data = new ArrayList<>();

        try {
            String modifiedBy = "";
            String role = "";
            String requestUserid = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
            if(!StringUtils.hasLength(requestUserid)){
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }

            Object rawRequest = requestBody.get(Constants.REQUEST);
            if (rawRequest instanceof List) {
                List<WfRequest> wfRequestList = mapper.convertValue(rawRequest, new TypeReference<List<WfRequest>>() {
                });
                logger.debug("Received {} workflow requests", wfRequestList.size());

                Map<String, List<WfRequest>> wfRequestsGroupedByUserId = wfRequestList.stream()
                        .filter(wfRequest -> wfRequest.getUserId() != null)
                        .collect(Collectors.groupingBy(WfRequest::getUserId));

                String serviceName = wfRequestList.stream()
                        .map(WfRequest::getServiceName)
                        .filter(wfServiceName -> wfServiceName != null && !wfServiceName.isEmpty())
                        .findFirst()
                        .orElse(null);

                if (StringUtils.hasLength(serviceName)) {
                    WfTransitionContext transitionContext = new WfTransitionContext(rootOrg, org, modifiedBy, role, userToken);
                    for (Map.Entry<String, List<WfRequest>> entry : wfRequestsGroupedByUserId.entrySet()) {
                        processWfRequestsForUser(transitionContext, entry.getKey(), entry.getValue(),
                                serviceName, data);
                    }
                    handlePostWorkflowNotification(wfRequestList.get(0));
                    response.put(Constants.MESSAGE, Constants.SUCCESS);
                    response.put(Constants.DATA, data);
                    response.put(Constants.STATUS, HttpStatus.OK);
                } else {
                    handleInvalidDataType(rawRequest, data, response);
                }
            } else {
                handleInvalidDataType(rawRequest, data, response);
            }
        } catch (Exception e) {
            logger.error("Error in workflowTransition for rootOrg: {}, org: {}", rootOrg, org, e);
            handleProcessingError(data, response);
        }
        return response;
    }

    private record WfTransitionContext(String rootOrg, String org, String modifiedBy, String role, String userToken) {
    }

    private void processWfRequestsForUser(WfTransitionContext context, String userId, List<WfRequest> wfRequests,
            String serviceName, List<Object> data) {
        logger.info("Processing workflow requests for userId: {}", userId);

        List<Object> wfRequestsForEvent = new ArrayList<>();
        for (WfRequest wfRequest : wfRequests) {
            logger.debug("Processing workflow request with wfId: {}", wfRequest.getWfId());
            if (processSingleWfRequestForEvent(context, wfRequest, wfRequestsForEvent, data)) {
                break;
            }
        }
        if (!CollectionUtils.isEmpty(wfRequestsForEvent)) {
            pushWorkflowEvents(serviceName, userId, wfRequestsForEvent, context.userToken());
        }
        logger.info("Completed workflowTransition successfully for userId: {}", userId);
    }

    private boolean processSingleWfRequestForEvent(WfTransitionContext context, WfRequest wfRequest,
            List<Object> wfRequestsForEvent, List<Object> data) {
        Map<String, Object> responseData = new HashMap<>();
        try {
            if (StringUtils.hasLength(wfRequest.getServiceName())) {
                if (!StringUtils.hasLength(wfRequest.getWfId()) &&
                        wfRequest.getServiceName().equalsIgnoreCase(Constants.PROFILE_SERVICE_NAME) &&
                        handleProfileServiceWorkflow(wfRequest, responseData)) {
                    logger.warn("Same Profile approval request is already in workflow for request ID: {}", wfRequest.getWfId());
                    return true;
                }
                Map<String, String> changeStatusResponse = changeStatus(context.rootOrg(), context.org(), wfRequest,
                        context.modifiedBy(), context.role(), context.userToken());
                String changedStatus = changeStatusResponse.get(Constants.STATUS);
                if (logger.isInfoEnabled()) {
                    logger.info("Changed status to '{}' for workflow ID: {}", changedStatus, changeStatusResponse.get(Constants.WF_ID_CONSTANT));
                }

                responseData.put(Constants.STATUS, changedStatus);
                responseData.put(Constants.WF_ID_CONSTANT, changeStatusResponse.get(Constants.WF_ID_CONSTANT));
            }
            wfRequestsForEvent.add(wfRequest);

            logger.info("sending notification request from here v2:{}", wfRequest);

        } catch (Exception e) {
            logger.error("Error processing workflow request ID: {}", wfRequest.getWfId(), e);
            responseData.put(Constants.STATUS, Constants.FAILED);
            responseData.put(Constants.ERROR_MESSAGE, e.getMessage());
            responseData.put(Constants.WF_ID_CONSTANT, StringUtils.hasLength(wfRequest.getWfId()) ? wfRequest.getWfId() : "");
        }
        data.add(responseData);
        return false;
    }

    private void handlePostWorkflowNotification(WfRequest wfRequest) {
        if (Constants.PROFILE_SERVICE_NAME.equalsIgnoreCase(wfRequest.getServiceName())
                && !Constants.WITHDRAW.equalsIgnoreCase(wfRequest.getAction())) {
            sendNotification(wfRequest);
        }
    }

    private void sendNotification(WfRequest wfRequest) {
        if (Objects.isNull(wfRequest) || CollectionUtils.isEmpty(wfRequest.getUpdateFieldValues())) {
            logger.info("wfRequest or updateFieldValues is null or empty.");
            return;
        }

        Object toValueObj = wfRequest.getUpdateFieldValues().get(0).get(Constants.TO_VALUE);
        if (!(toValueObj instanceof Map)) {
            logger.info("toValue is not a Map or is null.");
            return;
        }

        Map<String, Object> toValueMap = (Map<String, Object>) toValueObj;
        if (MapUtils.isEmpty(toValueMap)) {
            logger.info("toValue map is empty.");
            return;
        }

        String requestKey = toValueMap.keySet().iterator().next();

        switch (requestKey) {
            case Constants.GROUP, Constants.DESIGNATION:
                sendProfileUpdateNotification(wfRequest);
                break;
            case Constants.NAME:
                sendOrgTransferUpdateNotification(wfRequest);
                break;
            default:
                logger.info("No specific notification to send for request key: {}", requestKey);
        }
    }



    private void sendOrgTransferUpdateNotification(WfRequest wfRequest) {
        logger.info("Sending org transfer update notification for user: {}", wfRequest.getUserId());
        Map<String, Object> placeholder = new HashMap<>();
        String userId = wfRequest.getUserId();
        Map<String, Object> data = new HashMap<>();
        data.put("id", wfRequest.getUserId());
        notificationTriggerService.triggerNotification(Constants.TRANSFER_UPDATE, Constants.UPDATE,
                List.of(userId), data, placeholder);
    }

    private void sendProfileUpdateNotification(WfRequest wfRequest) {
        logger.info("Sending profile update verification notification for user: {}", wfRequest.getUserId());
        Map<String, Object> placeholder = new HashMap<>();
        String userId = wfRequest.getUserId();
        Map<String, Object> data = new HashMap<>();
        data.put("id", wfRequest.getUserId());
        notificationTriggerService.triggerNotification(Constants.PROFILE_UPDATE, Constants.UPDATE,
                List.of(userId),data,placeholder);
    }

    private void handleProcessingError(List<Object> data, Response response) {
        response.put(Constants.MESSAGE, "Failed to process workflow transition");
        response.put(Constants.DATA, data);
        response.put(Constants.STATUS, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void handleInvalidDataType(Object rawRequest, List<Object> data, Response response) {
        logger.error("Invalid data type for workflow requests: {}", rawRequest.getClass().getName());
        response.put(Constants.MESSAGE, "Invalid data type for workflow requests");
        response.put(Constants.DATA, data);
        response.put(Constants.STATUS, HttpStatus.BAD_REQUEST);
    }

    private void pushWorkflowEvents(String serviceName, String userId, List<Object> wfRequestsForEvent, String userToken) {
        try {
            Map<String, Object> workflowEvent = new HashMap<>();
            workflowEvent.put(Constants.USER_ID, userId);
            workflowEvent.put(Constants.SERVICE_NAME, serviceName);
            workflowEvent.put(Constants.WORKFLOW_REQUESTS, wfRequestsForEvent);
            workflowEvent.put(Constants.X_AUTH_TOKEN, userToken);

            logger.debug("Pushing workflow event to notification topic for userId: {}", userId);
            producer.push(configuration.getWorkFlowNotificationTopicV2(), workflowEvent);

            logger.debug("Pushing workflow event to application topic for userId: {}", userId);
            producer.push(configuration.getWorkflowApplicationTopicV2(), workflowEvent);
        } catch (Exception e) {
            logger.error("Error pushing workflow events for userId: {}", userId, e);
        }
    }


    private boolean handleProfileServiceWorkflow(WfRequest wfRequest, Map<String, Object> responseData) {
        try {
            Map<String, Object> wfRequestExistResponse = isWFRequestExist(wfRequest);
            boolean isWFRequestExist = (boolean) wfRequestExistResponse.get(Constants.IS_WF_REQUEST_EXIST);

            if (isWFRequestExist) {
                responseData.put(Constants.STATUS, Constants.SEND_FOR_APPROVAL);
                responseData.put(Constants.WF_ID_CONSTANT, wfRequestExistResponse.get(Constants.WF_ID_CONSTANT));
                return true;
            } else {
                addRequestTypeInProfileWF(wfRequest);
            }
        } catch (IOException e) {
            String errorMessage = String.format("Error while validating WF request for user: %s. Exception message: %s", wfRequest.getUserId(), e.getMessage());
            logger.error(errorMessage, e);
            throw new ApplicationException(errorMessage, e);
        }
        return false;
    }

    private Map<String, Object> isWFRequestExist(WfRequest wfRequest) throws IOException {
        return WorkflowHelperUtil.isWFRequestExist(wfRequest, wfStatusRepo, mapper);
    }

    private void addRequestTypeInProfileWF(WfRequest wfRequest) {
        WorkflowHelperUtil.addRequestTypeInProfileWF(wfRequest);
    }

    private Map<String, String> changeStatus(String rootOrg, String org, WfRequest wfRequest, String modifiedBy, String role, String userToken) {
        String wfId = wfRequest.getWfId();
        String nextState = null;
        Map<String, String> data = new HashMap<>();

        try {
            validateWfRequest(wfRequest);

            // Fetch existing workflow record
            WfStatusEntity applicationStatus = wfStatusRepo.findByRootOrgAndOrgAndApplicationIdAndWfId(
                    rootOrg, org, wfRequest.getApplicationId(), wfRequest.getWfId());

            // Resolve service name
            String serviceName = resolveServiceName(wfRequest, applicationStatus);

            // Set createdOn if existing status is found
            if (applicationStatus != null && applicationStatus.getCreatedOn() != null) {
                wfRequest.setCreatedOn(applicationStatus.getCreatedOn().toString());
            }

            // Workflow model and validation
            WorkFlowModel workFlowModel = getWorkFlowConfig(serviceName);
            WfStatus wfStatus = getWfStatus(wfRequest.getState(), workFlowModel);
            validateUserAndWfStatus(wfRequest, wfStatus, applicationStatus);

            // Get next state
            WfAction wfAction = getWfAction(wfRequest.getAction(), wfStatus);
            nextState = wfAction.getNextState();

            // Initialize application status if new
            if (applicationStatus == null) {
                applicationStatus = initializeApplicationStatus(rootOrg, org, wfRequest, serviceName);
                wfId = applicationStatus.getWfId();
            }

            // Update workflow status entity
            updateApplicationStatus(applicationStatus, wfRequest, nextState, modifiedBy, role, workFlowModel, userToken);
            // Handle specific fields if applicable
            handleSpecialFields(wfRequest);

        } catch (IOException e) {
            throw new ApplicationException(Constants.WORKFLOW_PARSING_ERROR_MESSAGE, e);
        }

        // Prepare response data
        data.put(Constants.STATUS, nextState);
        data.put(Constants.WF_ID_CONSTANT, wfId);
        return data;
    }

    private String resolveServiceName(WfRequest wfRequest, WfStatusEntity applicationStatus) {
        if (Constants.BLENDED_PROGRAM_SERVICE_NAME.equalsIgnoreCase(wfRequest.getServiceName())
                && applicationStatus != null && StringUtils.hasLength(applicationStatus.getServiceName())) {
            return applicationStatus.getServiceName();
        }
        return wfRequest.getServiceName();
    }

    private WfStatusEntity initializeApplicationStatus(String rootOrg, String org, WfRequest wfRequest, String serviceName) {
        WfStatusEntity applicationStatus = new WfStatusEntity();
        applicationStatus.setWfId(UUID.randomUUID().toString());
        applicationStatus.setServiceName(serviceName);
        applicationStatus.setUserId(wfRequest.getUserId());
        applicationStatus.setApplicationId(wfRequest.getApplicationId());
        applicationStatus.setRootOrg(rootOrg);
        applicationStatus.setOrg(org);
        applicationStatus.setCreatedOn(new Date());
        applicationStatus.setRequestType(wfRequest.getRequestType());
        wfRequest.setWfId(applicationStatus.getWfId());
        return applicationStatus;
    }

    private void updateApplicationStatus(WfStatusEntity applicationStatus, WfRequest wfRequest, String nextState,
                                         String modifiedBy, String role, WorkFlowModel workFlowModel, String userToken) throws IOException {
        WfStatus nextWfStatus = getWfStatus(nextState, workFlowModel);
        boolean inWorkflow = !nextWfStatus.getIsLastState();
        Date currentTime = new Date();

        applicationStatus.setLastUpdatedOn(currentTime);
        applicationStatus.setCurrentStatus(nextState);
        applicationStatus.setActorUUID(wfRequest.getActorUserId());
        applicationStatus.setUpdateFieldValues(mapper.writeValueAsString(wfRequest.getUpdateFieldValues()));
        applicationStatus.setInWorkflow(inWorkflow);
        applicationStatus.setDeptName(wfRequest.getDeptName());
        applicationStatus.setComment(wfRequest.getComment());
        addModificationEntry(applicationStatus, modifiedBy, wfRequest.getAction(), role);
        WfStatusEntity savedEntity = wfStatusRepo.save(applicationStatus);
        String userId = savedEntity.getUserId();

        if (Constants.ORG_TRANSFER_REQUEST.equalsIgnoreCase(applicationStatus.getRequestType()) && Boolean.TRUE.equals(nextWfStatus.getIsLastState())) {
            autoCompleteGroupDesignationAfterTransfer(applicationStatus, wfRequest, nextState, inWorkflow, currentTime, userId, userToken);
        }
    }

    private void autoCompleteGroupDesignationAfterTransfer(WfStatusEntity applicationStatus, WfRequest wfRequest,
            String nextState, boolean inWorkflow, Date currentTime, String userId, String userToken) {
        logger.info("Auto {} Group-Designation post Transfer Approval for userId: {}", nextState, applicationStatus.getUserId());

        List<WfStatusEntity> wfStatusEntities = wfStatusRepo.findByUserIdAndCurrentStatus(userId, Constants.SEND_FOR_APPROVAL, Boolean.TRUE);
        if (CollectionUtils.isEmpty(wfStatusEntities)) {
            logger.warn("No pending Group-Designation request found for userId: {}",
                    applicationStatus.getUserId());
            return;
        }

        List<WfStatusEntity> validEntities = new ArrayList<>();
        List<Object> wfRequestsForEvent = new ArrayList<>();
        for (WfStatusEntity wfStatusEntity : wfStatusEntities) {
            buildValidGroupDesignationEntity(wfStatusEntity, wfRequest, nextState, inWorkflow, currentTime, validEntities, wfRequestsForEvent);
        }

        // Save only valid entities
        if (!CollectionUtils.isEmpty(validEntities)) {
            wfStatusRepo.saveAll(validEntities);
        } else {
            logger.warn("No valid workflow entities to save for userId: {}", userId);
        }

        // Push workflow events
        if (!CollectionUtils.isEmpty(wfRequestsForEvent)) {
            try {
                pushWorkflowEvents(Constants.PROFILE_SERVICE_NAME, userId, wfRequestsForEvent, userToken);
                logger.info("Pushed {} workflow events for userId: {}", wfRequestsForEvent.size(), userId);
            } catch (Exception e) {
                logger.error("Failed to push workflow events for userId: {}, error={}", userId, e.getMessage(), e);
            }
        } else {
            logger.warn("No valid workflow requests for userId: {}", userId);
        }
    }

    private void buildValidGroupDesignationEntity(WfStatusEntity wfStatusEntity, WfRequest wfRequest, String nextState,
            boolean inWorkflow, Date currentTime, List<WfStatusEntity> validEntities, List<Object> wfRequestsForEvent) {
        try {
            String fieldValues = wfStatusEntity.getUpdateFieldValues();
            if (!StringUtils.hasText(fieldValues)) {
                logger.warn("Empty updateFieldValues | wfId={}, userId={}", wfStatusEntity.getWfId(), wfStatusEntity.getUserId());
                return;
            }

            List<HashMap<String, Object>> updateFieldValues = mapper.readValue(fieldValues, new TypeReference<List<HashMap<String, Object>>>() {});

            // Build workflow request object
            WfRequest wfRequestGD = new WfRequest();
            wfRequestGD.setWfId(wfStatusEntity.getWfId());
            wfRequestGD.setUserId(wfStatusEntity.getUserId());
            wfRequestGD.setApplicationId(wfStatusEntity.getApplicationId());
            wfRequestGD.setActorUserId(wfStatusEntity.getActorUUID());
            wfRequestGD.setComment(wfRequest.getComment());
            wfRequestGD.setServiceName(wfStatusEntity.getServiceName());
            wfRequestGD.setRequestType(wfStatusEntity.getRequestType());
            wfRequestGD.setDeptName(wfStatusEntity.getDeptName());
            wfRequestGD.setState(wfStatusEntity.getCurrentStatus());
            wfRequestGD.setAction(wfRequest.getAction());
            wfRequestGD.setUpdateFieldValues(updateFieldValues);
            wfRequestsForEvent.add(wfRequestGD);

            // Update entity fields only if parsing succeeded
            wfStatusEntity.setCurrentStatus(nextState);
            wfStatusEntity.setInWorkflow(inWorkflow);
            wfStatusEntity.setLastUpdatedOn(currentTime);
            validEntities.add(wfStatusEntity);

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse updateFieldValues | wfId={}, userId={}, error={}", wfStatusEntity.getWfId(), wfStatusEntity.getUserId(), e.getMessage(), e);
        }
    }

    private void handleSpecialFields(WfRequest wfRequest) {
        wfRequest.getUpdateFieldValues().stream()
                .filter(updatedValue -> updatedValue.containsKey(Constants.TO_VALUE))
                .map(updatedValue -> (Map<String, Object>) updatedValue.get(Constants.TO_VALUE))
                .findFirst()
                .ifPresent(toValue -> {
                    String fieldKey = toValue.keySet().iterator().next();
                    if (Constants.NAME.equalsIgnoreCase(fieldKey)) {
                        Map<String, Object> propertyMap = new HashMap<>();
                        propertyMap.put(Constants.ID, wfRequest.getUserId());
                        List<Map<String, Object>> userDetails = cassandraOperation.getRecordsByProperties(
                                Constants.KEYSPACE_SUNBIRD, Constants.USER_TABLE, propertyMap,
                                Arrays.asList(Constants.ROOT_ORG_ID));
                        if (!userDetails.isEmpty()) {
                            wfRequest.setPreviousRootOrgId((String) userDetails.get(0).get(Constants.USER_ROOT_ORG_ID));
                        }
                    }
                });
    }


    private void validateWfRequest(WfRequest wfRequest) {
        WorkflowHelperUtil.validateWfRequest(wfRequest);
    }

    public WorkFlowModel getWorkFlowConfig(String serviceName) {
        try {
            StringBuilder uri = new StringBuilder();
            WorkflowHelperUtil.appendBaseWorkflowConfigUri(configuration, uri, serviceName);
            WorkflowHelperUtil.appendMultilevelEnrolConfigUri(configuration, uri, serviceName);
            return WorkflowHelperUtil.fetchAndParseWorkFlowConfig(requestServiceImpl, mapper, uri);
        } catch (Exception e) {
            logger.error("Exception occurred while getting work flow config details!");
            throw new ApplicationException(Constants.WORKFLOW_PARSING_ERROR_MESSAGE, e);
        }
    }

    /**
     * Get Workflow Action based on given action
     *
     * @param action
     * @param wfStatus
     * @return Work flow Action
     */
    private WfAction getWfAction(String action, WfStatus wfStatus) {
        return WorkflowHelperUtil.getWfAction(action, wfStatus);
    }

    /**
     * Get the workflow State based on given state
     *
     * @param state
     * @param workFlowModel
     * @return Workflow State
     */
    private WfStatus getWfStatus(String state, WorkFlowModel workFlowModel) {
        return WorkflowHelperUtil.getWfStatus(state, workFlowModel);
    }

    /**
     * Validate application against workflow state
     *
     * @param wfRequest
     * @param wfStatus
     * @param applicationStatus
     */
    private void validateUserAndWfStatus(WfRequest wfRequest, WfStatus wfStatus, WfStatusEntity applicationStatus) {

        if (!StringUtils.hasLength(wfRequest.getWfId()) && !Boolean.TRUE.equals(wfStatus.getStartState())) {
            throw new ApplicationException(Constants.WORKFLOW_ID_ERROR_MESSAGE);
        }
        if ((!ObjectUtils.isEmpty(applicationStatus))
                && (!wfRequest.getState().equalsIgnoreCase(applicationStatus.getCurrentStatus()))) {
            throw new BadRequestException("Application is in " + applicationStatus.getCurrentStatus()
                    + " State but trying to be move in " + wfRequest.getState() + " state!");
        }
    }

    private void addModificationEntry(WfStatusEntity applicationStatus, String userId, String action, String role) throws IOException {
        WorkflowHelperUtil.addModificationEntry(configuration, mapper, applicationStatus, userId, action, role);
    }

    public void processAndUpdateProfessionalDetails(String userId, String updateFieldValuesJson) {
        try {
            JsonNode updateFieldValuesArray = mapper.readTree(updateFieldValuesJson);

            for (JsonNode updateEntry : updateFieldValuesArray) {
                String fieldKey = updateEntry.get(Constants.FIELD_KEY).asText();

                if (Constants.PROFESSIONAL_DETAILS.equals(fieldKey)) {
                    JsonNode toValueNode = updateEntry.get(Constants.TO_VALUE);

                    String group = toValueNode.has(Constants.GROUP) ? toValueNode.get(Constants.GROUP).asText() : null;
                    String designation = toValueNode.has(Constants.DESIGNATION) ? toValueNode.get(Constants.DESIGNATION).asText() : null;

                    updateProfessionalDetailsInCache(userId, group, designation);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing update_field_values JSON", e);
        }
    }

    public void updateProfessionalDetailsInCache(String userId, String group, String designation) {
        String key = Constants.BASIC_PROFILE_KEY + userId;

        try  {
            String cachedData = redisCacheMgr.getContentFromCache(key);
            if (cachedData == null) {
                logger.warn("No cache found for user {}", userId);
                return;
            }
            ObjectNode profileNode = (ObjectNode) mapper.readTree(cachedData);
            applyProfessionalDetailsUpdate(profileNode, group, designation);
            redisCacheMgr.putInBasicProfileCache(key, mapper.writeValueAsString(profileNode), configuration.getBasicProfileCacheTtl());
        } catch (Exception e) {
            logger.error("Error updating professionalDetails in Redis cache", e);
        }
    }

    private void applyProfessionalDetailsUpdate(ObjectNode profileNode, String group, String designation) {
        if (profileNode == null || !profileNode.has(Constants.PROFESSIONAL_DETAILS)) {
            return;
        }
        ArrayNode professionalDetailsArray = (ArrayNode) profileNode.get(Constants.PROFESSIONAL_DETAILS);

        for (JsonNode profDetail : professionalDetailsArray) {
            ObjectNode profDetailObj = (ObjectNode) profDetail;
            if (group != null) profDetailObj.put(Constants.GROUP, group);
            if (designation != null) profDetailObj.put(Constants.DESIGNATION, designation);
        }
    }


}
