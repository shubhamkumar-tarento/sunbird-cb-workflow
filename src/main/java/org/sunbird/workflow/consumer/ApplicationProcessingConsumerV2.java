package org.sunbird.workflow.consumer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.service.impl.ApplicationProcessingServiceImplV2;
import org.sunbird.workflow.service.impl.WorkflowAuditProcessingServiceImpl;
import org.sunbird.workflow.service.impl.WorkflowESSyncServiceImpl;
import org.sunbird.workflow.utils.ApplicationProcessingConsumerUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ApplicationProcessingConsumerV2 {

    Logger logger = LogManager.getLogger(ApplicationProcessingConsumerV2.class);

    private final ObjectMapper mapper;
    private final ApplicationProcessingServiceImplV2 applicationProcessingServiceImplV2;
    private final WorkflowAuditProcessingServiceImpl workflowAuditProcessingService;
    private final WorkflowESSyncServiceImpl workflowESSyncServiceImpl;

    @KafkaListener(groupId = "workflowContentTopic-consumer-v2", topics = "${kafka.topics.workflow.request.v2}")
    public void processMessage(ConsumerRecord<String, String> data) {
        ApplicationProcessingConsumerUtil.processMessage(data, logger, this::processApplicationMessage);
    }

    private void processApplicationMessage(String strData) {
        logger.info("Received data in user profile consumer: {}", strData);
        try {
            Map<String, Object> workflowEventObj = mapper.readValue(strData, new TypeReference<Map<String, Object>>() {
            });
            String userId = (String) workflowEventObj.get(Constants.USER_ID);
            String serviceName = (String) workflowEventObj.get(Constants.SERVICE_NAME);
            String userToken = (String) workflowEventObj.get(Constants.X_AUTH_TOKEN);
            @SuppressWarnings("unchecked")
            List<WfRequest> wfRequests = mapper.convertValue(workflowEventObj.get(Constants.WORKFLOW_REQUESTS),
                    new TypeReference<List<WfRequest>>() {
                    });

            processWfRequests(wfRequests, serviceName, userId, userToken);
        } catch (IOException e) {
            logger.error("Error deserializing workflow event data: {}", strData, e);
        } catch (ClassCastException e) {
            logger.error("Invalid data structure in workflow event: {}", strData, e);
        } catch (Exception e) {
            logger.error("Unexpected error while processing application message: {}", strData, e);
        }
    }

    private void processWfRequests(List<WfRequest> wfRequests, String serviceName, String userId, String userToken) {
        try {
            logger.debug("Processing workflow request for user ID: {}", userId);

            applicationProcessingServiceImplV2.processWfApplicationRequest(wfRequests, serviceName, userId, userToken);
            applicationProcessingServiceImplV2.updateDepartmentToPortalDBs(wfRequests);
            createAudit(wfRequests);
            wfRequests.forEach(workflowESSyncServiceImpl::syncWithElasticService);
            logger.debug("Successfully processed workflow request with user ID: {}", userId);
        } catch (Exception e) {
            logger.error("Error processing workflow request with wf ID: {}", userId, e);
        }
    }

    private void createAudit(List<WfRequest> wfRequestList) {
        wfRequestList.forEach(workflowAuditProcessingService::createAudit);
    }
}
