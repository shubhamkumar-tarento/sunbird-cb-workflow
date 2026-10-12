package org.sunbird.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.exception.ApplicationException;
import org.sunbird.workflow.exception.BadRequestException;
import org.sunbird.workflow.exception.InvalidDataInputException;
import org.sunbird.workflow.models.AiAssessmentApprovalEvent;
import org.sunbird.workflow.models.Response;
import org.sunbird.workflow.models.SearchCriteria;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.postgres.entity.WfStatusCountDTO;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfAuditRepo;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.producer.Producer;
import org.sunbird.workflow.service.impl.RequestServiceImpl;
import org.sunbird.workflow.service.impl.WorkflowServiceImpl;
import org.sunbird.workflow.utils.AccessTokenValidator;
import org.sunbird.workflow.utils.CassandraOperation;
import org.sunbird.workflow.utils.ElasticsearchServiceManager;
import org.sunbird.workflow.utils.LRUCache;
import org.sunbird.workflow.utils.NotificationTriggerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiAssessmentServiceImpl extends WorkflowServiceImpl {

    private static final Logger aiLog = LogManager.getLogger(AiAssessmentServiceImpl.class);

    private final AccessTokenValidator accessTokenValidator;
    private final Configuration configuration;
    private final WfStatusRepo wfStatusRepo;
    private final UserProfileWfService userProfileWfService;
    private final Producer producer;

    public AiAssessmentServiceImpl(WfStatusRepo wfStatusRepo, WfAuditRepo wfAuditRepo, ObjectMapper mapper,
                                   Configuration configuration, @Lazy UserProfileWfService userProfileWfService,
                                   RequestServiceImpl requestServiceImpl, Producer producer,
                                   CassandraOperation cassandraOperation, AccessTokenValidator accessTokenValidator,
                                   LRUCache<String, List<WfStatusCountDTO>> localCache, StorageService storageService,
                                   Producer kafkaProducer, ElasticsearchServiceManager eServiceManager,
                                   NotificationTriggerService notificationTriggerService) {
        super(wfStatusRepo, wfAuditRepo, mapper, configuration, userProfileWfService, requestServiceImpl, producer,
                cassandraOperation, accessTokenValidator, localCache, storageService, kafkaProducer, eServiceManager,
                notificationTriggerService);
        this.accessTokenValidator = accessTokenValidator;
        this.configuration = configuration;
        this.wfStatusRepo = wfStatusRepo;
        this.userProfileWfService = userProfileWfService;
        this.producer = producer;
    }

    public Response aiAssessmentWorkflowTransition(WfRequest wfRequest, String token) {
        List<String> actorRoles = accessTokenValidator.fetchUserRolesFromToken(token);
        aiLog.info("Actor roles: {}", actorRoles);
        validateRoles(wfRequest.getAction(), actorRoles);
        validateAiAssessmentWfRequest(wfRequest);
        Response response = workflowTransition(wfRequest.getRootOrgId(), wfRequest.getRootOrgId(), wfRequest);
        if (Constants.APPROVE.equalsIgnoreCase(wfRequest.getAction())) {
            producer.push(configuration.getAiAssessmentTopic(),
                    new AiAssessmentApprovalEvent(wfRequest, token));
            aiLog.info("Pushed to AI Assessment topic for APPROVED userId: {}", wfRequest.getUserId());
        }
        return response;
    }

    private void validateRoles(String action, List<String> actorRoles) {
        List<String> initiateRoles = configuration.getAiAssessmentInitiateRoles();
        List<String> approveRejectRoles = configuration.getAiAssessmentApproveRejectRoles();

        if (Constants.INITIATE.equalsIgnoreCase(action)) {
            boolean hasRole = actorRoles.stream()
                    .anyMatch(initiateRoles::contains);
            if (!hasRole) {
                aiLog.error("Unauthorized INITIATE by roles: {}", actorRoles);
                throw new BadRequestException(
                        "Roles " + initiateRoles +
                                " are allowed to initiate AI Assessment requests");
            }

        } else if (Constants.APPROVE.equalsIgnoreCase(action)
                || Constants.REJECT.equalsIgnoreCase(action)) {
            boolean hasRole = actorRoles.stream()
                    .anyMatch(approveRejectRoles::contains);
            if (!hasRole) {
                aiLog.error("Unauthorized {} by roles: {}", action, actorRoles);
                throw new BadRequestException(
                        "Roles " + approveRejectRoles +
                                " are allowed to approve or reject " +
                                "AI Assessment requests");
            }

        } else {
            throw new BadRequestException("Invalid action: " + action);
        }
    }

    public Response fetchAiAssessement(String token, SearchCriteria criteria) {
        String actorUserId = accessTokenValidator.fetchUserIdFromAccessToken(token);
        if (StringUtils.isEmpty(actorUserId)) {
            throw new BadRequestException("Invalid or expired token");
        }

        List<String> actorRoles = accessTokenValidator.fetchUserRolesFromToken(token);
        aiLog.info("Actor: {} roles: {}", actorUserId, actorRoles);

        List<String> approveRejectRoles = configuration.getAiAssessmentApproveRejectRoles();
        boolean hasRole = actorRoles.stream().anyMatch(approveRejectRoles::contains);
        if (!hasRole) {
            throw new BadRequestException("Only SPV Publisher can access AI Assessment requests");
        }
        return getAiAssessmentRequests(criteria);
    }

    public Response getAiAssessmentRequestByUserId(String token) {
        String userId = null;
        try {
            validateTokenAndRoles(token, configuration.getAiAssessmentInitiateRoles());
            userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isEmpty(userId)) {
                throw new BadRequestException("Invalid or expired token");
            }
            List<WfStatusEntity> entities = wfStatusRepo
                    .findByUserIdAndServiceNameOrderByLastUpdatedOnDesc(
                            userId,
                            Constants.AI_ASSESSMENT_SERVICE_NAME);

            if (CollectionUtils.isEmpty(entities)) {
                Response response = new Response();
                response.put(Constants.MESSAGE, Constants.SUCCESSFUL);
                response.put(Constants.DATA, new ArrayList<>());
                response.put(Constants.COUNT, 0);
                response.put(Constants.STATUS, HttpStatus.OK);
                return response;
            }

            Map<String, List<WfStatusEntity>> groupedEntities = entities
                    .stream()
                    .collect(Collectors.groupingBy(
                            WfStatusEntity::getApplicationId));

            List<Map<String, Object>> userProfiles = userProfileWfService.enrichUserData(groupedEntities, null);

            Response response = new Response();
            response.put(Constants.MESSAGE, Constants.SUCCESSFUL);
            response.put(Constants.DATA, userProfiles);
            response.put(Constants.COUNT, userProfiles.size());
            response.put(Constants.STATUS, HttpStatus.OK);
            return response;

        } catch (Exception e) {
            aiLog.error("Error fetching AI Assessment request for userId: {}", userId, e);
            throw new ApplicationException(
                    "Error fetching AI Assessment request for user", e);
        }
    }

    private String validateTokenAndRoles(String token, List<String> allowedRoles) {
        String actorUserId = accessTokenValidator
                .fetchUserIdFromAccessToken(token);
        if (StringUtils.isEmpty(actorUserId)) {
            throw new BadRequestException("Invalid or expired token");
        }

        List<String> actorRoles = accessTokenValidator
                .fetchUserRolesFromToken(token);
        aiLog.info("Actor: {} roles: {}", actorUserId, actorRoles);

        boolean hasRole = actorRoles.stream()
                .anyMatch(allowedRoles::contains);
        if (!hasRole) {
            throw new BadRequestException(
                    "Not authorized to perform this action");
        }

        return actorUserId;
    }

    private void validateAiAssessmentWfRequest(WfRequest wfRequest) {
        if (StringUtils.isEmpty(wfRequest.getUserId())) {
            throw new InvalidDataInputException("userId is mandatory");
        }
        if (StringUtils.isEmpty(wfRequest.getServiceName())) {
            throw new InvalidDataInputException("serviceName is mandatory");
        }
        if (!Constants.AI_ASSESSMENT_SERVICE_NAME
                .equalsIgnoreCase(wfRequest.getServiceName())) {
            throw new InvalidDataInputException(
                    "serviceName must be " + Constants.AI_ASSESSMENT_SERVICE_NAME);
        }
        if (StringUtils.isEmpty(wfRequest.getState())) {
            throw new InvalidDataInputException("state is mandatory");
        }
        if (StringUtils.isEmpty(wfRequest.getAction())) {
            throw new InvalidDataInputException("action is mandatory");
        }
        if (StringUtils.isEmpty(wfRequest.getRootOrgId())) {
            throw new InvalidDataInputException("rootOrgId is mandatory");
        }
        if (CollectionUtils.isEmpty(wfRequest.getUpdateFieldValues())) {
            throw new InvalidDataInputException("updateFieldValues is mandatory");
        }
        aiLog.info("AI Assessment WfRequest validation passed for userId: {}",
                wfRequest.getUserId());
    }
}
