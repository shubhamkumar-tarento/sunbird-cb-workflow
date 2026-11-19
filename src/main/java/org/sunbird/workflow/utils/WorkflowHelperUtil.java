package org.sunbird.workflow.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.MapUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.exception.BadRequestException;
import org.sunbird.workflow.exception.InvalidDataInputException;
import org.sunbird.workflow.models.WfAction;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.models.WfStatus;
import org.sunbird.workflow.models.WorkFlowModel;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.service.impl.RequestServiceImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared logic extracted from WorkflowServiceImpl, WorkFlowServiceImplV2 and
 * BPWorkFlowServiceImpl to remove duplication between those classes. Every
 * method here is a direct, behavior-preserving extraction of code that was
 * previously copy-pasted across those classes.
 */
public final class WorkflowHelperUtil {

    private WorkflowHelperUtil() {
    }

    /**
     * Get Workflow Action based on given action
     */
    public static WfAction getWfAction(String action, WfStatus wfStatus) {
        WfAction wfAction = null;
        if (ObjectUtils.isEmpty(wfStatus.getActions())) {
            throw new BadRequestException(Constants.WORKFLOW_ACTION_ERROR);
        }
        for (WfAction filterAction : wfStatus.getActions()) {
            if (action.equals(filterAction.getAction())) {
                wfAction = filterAction;
            }
        }
        if (ObjectUtils.isEmpty(wfAction)) {
            throw new BadRequestException(Constants.WORKFLOW_ACTION_ERROR);
        }
        return wfAction;
    }

    /**
     * Get the workflow State based on given state
     */
    public static WfStatus getWfStatus(String state, WorkFlowModel workFlowModel) {
        WfStatus wfStatus = null;
        for (WfStatus status : workFlowModel.getWfstates()) {
            if (status.getState().equals(state)) {
                wfStatus = status;
                break;
            }
        }
        if (ObjectUtils.isEmpty(wfStatus)) {
            throw new BadRequestException(Constants.WORKFLOW_STATE_CHECK_ERROR);
        }
        return wfStatus;
    }

    /**
     * Validate the workflow request
     */
    public static void validateWfRequest(WfRequest wfRequest) {
        if (!StringUtils.hasLength(wfRequest.getState())) {
            throw new InvalidDataInputException(Constants.STATE_VALIDATION_ERROR);
        }
        if (!StringUtils.hasLength(wfRequest.getApplicationId())) {
            throw new InvalidDataInputException(Constants.APPLICATION_ID_VALIDATION_ERROR);
        }
        if (!StringUtils.hasLength(wfRequest.getActorUserId())) {
            throw new InvalidDataInputException(Constants.ACTOR_UUID_VALIDATION_ERROR);
        }
        if (!StringUtils.hasLength(wfRequest.getUserId())) {
            throw new InvalidDataInputException(Constants.USER_UUID_VALIDATION_ERROR);
        }
        if (!StringUtils.hasLength(wfRequest.getAction())) {
            throw new InvalidDataInputException(Constants.ACTION_VALIDATION_ERROR);
        }
        if (!Constants.WITHDRAW.equalsIgnoreCase(wfRequest.getAction()) && CollectionUtils.isEmpty(wfRequest.getUpdateFieldValues())) {
            throw new InvalidDataInputException(Constants.FIELD_VALUE_VALIDATION_ERROR);
        }
        if (!StringUtils.hasLength(wfRequest.getServiceName())) {
            throw new InvalidDataInputException(Constants.WORKFLOW_SERVICENAME_VALIDATION_ERROR);
        }
    }

    /**
     * Appends the URI fragment for the "base" workflow-config service names
     * (profile/user-registration/position/organisation/domain/blended-program).
     * A no-op (leaves {@code uri} unchanged) for any other serviceName.
     */
    public static void appendBaseWorkflowConfigUri(Configuration configuration, StringBuilder uri, String serviceName) {
        switch (serviceName) {
            case Constants.PROFILE_SERVICE_NAME:
                uri.append(configuration.getLmsServiceHost() + configuration.getProfileServiceConfigPath());
                break;
            case Constants.USER_REGISTRATION_SERVICE_NAME:
                uri.append(configuration.getLmsServiceHost() + configuration.getUserRegistrationServiceConfigPath());
                break;
            case Constants.POSITION_SERVICE_NAME:
                uri.append(configuration.getLmsServiceHost() + configuration.getPositionServiceConfigPath());
                break;
            case Constants.ORGANISATION_SERVICE_NAME:
                uri.append(configuration.getLmsServiceHost() + configuration.getOrgServiceConfigPath());
                break;
            case Constants.DOMAIN_SERVICE_NAME:
                uri.append(configuration.getLmsServiceHost() + configuration.getDomainServiceConfigPath());
                break;
            case Constants.BLENDED_PROGRAM_SERVICE_NAME:
                uri.append(configuration.getLmsServiceHost() + configuration.getBlendedProgramServicePath());
                break;
            default:
                break;
        }
    }

    /**
     * Appends the URI fragment for the "multilevel BP enrol" workflow-config
     * service names. A no-op (leaves {@code uri} unchanged) for any other serviceName.
     */
    public static void appendMultilevelEnrolConfigUri(Configuration configuration, StringBuilder uri, String serviceName) {
        switch (serviceName) {
            case Constants.ONE_STEP_PC_APPROVAL:
                uri.append(configuration.getLmsServiceHost()).append(configuration.getMultilevelBPEnrolEndPoint()).append(Constants.ONE_STEP_PC_APPROVAL);
                break;
            case Constants.ONE_STEP_MDO_APPROVAL:
                uri.append(configuration.getLmsServiceHost()).append(configuration.getMultilevelBPEnrolEndPoint()).append(Constants.ONE_STEP_MDO_APPROVAL);
                break;
            case Constants.TWO_STEP_MDO_AND_PC_APPROVAL:
                uri.append(configuration.getLmsServiceHost()).append(configuration.getMultilevelBPEnrolEndPoint()).append(Constants.TWO_STEP_MDO_AND_PC_APPROVAL);
                break;
            case Constants.TWO_STEP_PC_AND_MDO_APPROVAL:
                uri.append(configuration.getLmsServiceHost()).append(configuration.getMultilevelBPEnrolEndPoint()).append(Constants.TWO_STEP_PC_AND_MDO_APPROVAL);
                break;
            default:
                break;
        }
    }

    /**
     * Fetches the workflow-config document at {@code uri} and parses it into a {@link WorkFlowModel}.
     */
    public static WorkFlowModel fetchAndParseWorkFlowConfig(RequestServiceImpl requestServiceImpl, ObjectMapper mapper, StringBuilder uri) throws JsonProcessingException {
        Map<String, Object> wfConfig = (Map<String, Object>) requestServiceImpl.fetchResultUsingGet(uri);
        Map<String, Object> result = (Map<String, Object>) wfConfig.get(Constants.RESULT);
        Map<String, Object> response = (Map<String, Object>) result.get(Constants.RESPONSE);
        Map<String, Object> wfStates = mapper.readValue((String) response.get(Constants.VALUE), Map.class);
        return mapper.convertValue(wfStates, new TypeReference<WorkFlowModel>() {
        });
    }

    /**
     * Appends a modification-history entry to {@code applicationStatus}, if the
     * action is one of the configured modification-record-allow-actions.
     */
    public static void addModificationEntry(Configuration configuration, ObjectMapper mapper, WfStatusEntity applicationStatus,
            String userId, String action, String role) throws IOException {
        if (StringUtils.hasLength(userId) &&
                Arrays.asList(configuration.getModificationRecordAllowActions().split(Constants.COMMA)).contains(action)) {
            List<Map<String, Object>> historyMap;
            String history = applicationStatus.getModificationHistory();
            if (StringUtils.hasLength(history)) {
                historyMap = mapper.readValue(history, List.class);
            } else {
                historyMap = new ArrayList<>();
            }
            Map<String, Object> newModification = new HashMap<>();
            newModification.put("modifiedDate", new Date());
            newModification.put("modifiedBy", userId);
            newModification.put("action", action);
            newModification.put("role", role);
            historyMap.add(newModification);
            applicationStatus.setModificationHistory(mapper.writeValueAsString(historyMap));
        }
    }

    public static void addRequestTypeInProfileWF(WfRequest wfRequest) {
        String requestKey = ((Map<String, Object>) wfRequest.getUpdateFieldValues().get(0).get(Constants.TO_VALUE)).keySet().iterator().next();
        switch (requestKey) {
            case "group":
                wfRequest.setRequestType("GROUP_CHANGE");
                break;
            case "designation":
                wfRequest.setRequestType("DESIGNATION_CHANGE");
                break;
            case "name":
                wfRequest.setRequestType("ORG_TRANSFER");
                break;
            default:
                wfRequest.setRequestType(requestKey);
        }
    }

    public static Map<String, Object> isWFRequestExist(WfRequest wfRequest, WfStatusRepo wfStatusRepo, ObjectMapper mapper) throws IOException {
        if (wfRequest == null || wfRequest.getUpdateFieldValues() == null || wfRequest.getUpdateFieldValues().isEmpty()) {
            throw new IllegalArgumentException("Invalid WfRequest or updateFieldValues");
        }

        Map<String, Object> response = new HashMap<>();
        String newRequestKey = ((Map<String, Object>) wfRequest.getUpdateFieldValues().get(0).get(Constants.TO_VALUE)).keySet().iterator().next();
        List<WfStatusEntity> wfStatusEntities = wfStatusRepo.getPendingRequests(wfRequest.getUserId(), wfRequest.getServiceName(), Constants.SEND_FOR_APPROVAL);

        for (WfStatusEntity wfStatusEntity : wfStatusEntities) {
            String existingRequestKey = getKeyFromUpdateFieldValues(wfStatusEntity.getUpdateFieldValues(), mapper);
            if (org.apache.commons.lang3.StringUtils.isNotEmpty(existingRequestKey) && existingRequestKey.equalsIgnoreCase(newRequestKey)) {
                response.put(Constants.IS_WF_REQUEST_EXIST, true);
                response.put(Constants.WF_ID_CONSTANT, wfStatusEntity.getWfId());
                return response;
            }
        }
        response.put(Constants.IS_WF_REQUEST_EXIST, false);
        response.put(Constants.WF_ID_CONSTANT, wfRequest.getWfId());
        return response;
    }

    private static String getKeyFromUpdateFieldValues(String updateFieldValues, ObjectMapper mapper) throws IOException {
        List<Map<String, Object>> updatedFieldValues = mapper.readValue(updateFieldValues, new TypeReference<List<Map<String, Object>>>() {
        });
        Map<String, Object> toValue = (Map<String, Object>) updatedFieldValues.get(0).get(Constants.TO_VALUE);

        if (!MapUtils.isEmpty(toValue)) {
            return toValue.keySet().iterator().next();
        } else {
            return null;
        }
    }
}
