package org.sunbird.workflow.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.service.impl.NotificationServiceImpl;
import org.sunbird.workflow.service.impl.RequestServiceImpl;
import org.sunbird.workflow.service.impl.UserProfileWfServiceImpl;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class CommunityModeratorTransferApprovedConsumer {

    private static final Logger logger = LogManager.getLogger(CommunityModeratorTransferApprovedConsumer.class);

    private final ObjectMapper mapper;
    private final Configuration configuration;
    private final RequestServiceImpl requestServiceImpl;
    private final UserProfileWfServiceImpl userProfileWfService;
    private final NotificationServiceImpl notificationUtil;

    @KafkaListener(topics = "${kafka.topics.community.moderator.transfer}", groupId = "community-moderator-transfer-group")
    public void processMessage(ConsumerRecord<String, String> data) {
        try {
            if (StringUtils.isNotBlank(data.value())) {
                CompletableFuture.runAsync(() -> processNotification(data.value()));
            } else {
                logger.error("Empty message received in community moderator transfer consumer.");
            }
        } catch (Exception e) {
            logger.error("Error while processing community moderator transfer event: {}", e.getMessage(), e);
        }
    }

    private void processNotification(String payload) {
        try {
            logger.info("Received payload: {}", payload);

            JsonNode jsonNode = mapper.readTree(payload);
            if (jsonNode.isArray()) {
                List<WfRequest> requests = mapper.readValue(payload, new TypeReference<List<WfRequest>>() {});
                for (WfRequest wfRequest : requests) {
                    if (Constants.APPROVE_STATE.equalsIgnoreCase(wfRequest.getAction()) || Constants.INITIATE.equalsIgnoreCase(wfRequest.getAction())) {
                        processApprovedTransfer(wfRequest);
                    }
                }
            } else {
                WfRequest wfRequest = mapper.treeToValue(jsonNode, WfRequest.class);
                if (Constants.APPROVE_STATE.equalsIgnoreCase(wfRequest.getAction()) || Constants.INITIATE.equalsIgnoreCase(wfRequest.getAction())) {
                    processApprovedTransfer(wfRequest);
                }
            }
        } catch (Exception e) {
            logger.error("Exception in processing approved transfer notification", e);
        }
    }

    private void processApprovedTransfer(WfRequest wfRequest) {
        try {
            logger.info("Processing approved transfer for userId: {}", wfRequest.getUserId());

            Map<String, Object> userSearchRequest = buildUserSearchRequest(wfRequest.getUserId());
            StringBuilder userSearchUrl = new StringBuilder(configuration.getLmsServiceHost())
                    .append(configuration.getLmsUserSearchEndPoint());

            Map<String, Object> userResponse = (Map<String, Object>) requestServiceImpl
                    .fetchResultUsingPost(userSearchUrl, userSearchRequest, Map.class, null);

            if (!"OK".equalsIgnoreCase((String) userResponse.get(Constants.RESPONSE_CODE))) {
                logger.warn("User search failed for userId: {}", wfRequest.getUserId());
                return;
            }

            List<String> communityIds = extractCommunityIds(userResponse);
            if (communityIds.isEmpty()) {
                logger.info("No community memberships found for user: {}", wfRequest.getUserId());
                return;
            }

            for (String communityId : communityIds) {
                fetchModeratorsAndNotifyMdoLeaders(communityId, wfRequest);
            }

        } catch (Exception e) {
            logger.error("Failed to process transfer for userId: {}", wfRequest.getUserId(), e);
        }
    }

    private void fetchModeratorsAndNotifyMdoLeaders(String communityId, WfRequest wfRequest) {
        try {
            StringBuilder communityReadUrl = new StringBuilder(configuration.getCommunityServiceBaseUrl())
                    .append(configuration.getCommunityReadEndpoint()).append(communityId);

            Map<String, Object> response = (Map<String, Object>) requestServiceImpl
                    .fetchResultUsingGet(communityReadUrl);

            Map<String, Object> communityDetails = (Map<String, Object>) ((Map<String, Object>) response.get(Constants.RESULT)).get(Constants.COMMUNITY_DETAILS);
            String orgId = (String) communityDetails.get(Constants.ORG_ID);
            String communityName = (String) communityDetails.get(Constants.COMMUNITY_NAME);
            String createdOn = (String) communityDetails.get(Constants.COMMUNITY_NAME);
            List<Map<String, Object>> moderators = (List<Map<String, Object>>) communityDetails.get(Constants.MODERATORS);

            if (StringUtils.isNotBlank(orgId)) {
                List<String> mdoLeaders = userProfileWfService.getMdoAdminAndPCDetails(orgId, Collections.singletonList(Constants.MDO_LEADER));
                if (!CollectionUtils.isEmpty(mdoLeaders)) {
                    notificationUtil.sendNotificationToMdoLeader(mdoLeaders, wfRequest, communityName, createdOn, moderators);
                } else {
                    logger.info("No MDO admins found for orgId: {}", orgId);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to fetch community details for communityId: {}", communityId, e);
        }
    }

    private Map<String, Object> buildUserSearchRequest(String userId) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("userId", Collections.singletonList(userId));
        Map<String, Object> req = new HashMap<>();
        req.put("filters", filters);
        Map<String, Object> request = new HashMap<>();
        request.put("request", req);
        return request;
    }

    private List<String> extractCommunityIds(Map<String, Object> userResponse) {
        List<Map<String, Object>> contentList = (List<Map<String, Object>>)
                ((Map<String, Object>) ((Map<String, Object>) userResponse.get(Constants.RESULT)).get(Constants.RESPONSE)).get(Constants.CONTENT);

        return contentList.stream()
                .filter(map -> map.containsKey(Constants.DISCUSSION_COMMUNITIES))
                .flatMap(map -> ((List<String>) map.get(Constants.DISCUSSION_COMMUNITIES)).stream())
                .distinct()
                .toList();
    }
}
