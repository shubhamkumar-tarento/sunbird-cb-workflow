package org.sunbird.workflow.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.config.Constants;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.service.impl.NotificationServiceImpl;
import org.sunbird.workflow.utils.CassandraOperation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class NotificationConsumerV2 {

    Logger logger = LogManager.getLogger(NotificationConsumerV2.class);

    private final ObjectMapper mapper;
    private final NotificationServiceImpl notificationService;
    private final CassandraOperation cassandraOperation;
    private final Configuration configuration;

    @KafkaListener(groupId = "workflowNotificationTopic-consumer-v2", topics = "${kafka.topics.workflow.notification.v2}")
    public void processMessage(ConsumerRecord<String, String> data) {
        try {
            if (StringUtils.isNoneBlank(data.value())) {
                CompletableFuture.runAsync(() -> processNotificationRequest(data.value()));
            } else {
                logger.error("Error in Notification Consumer: Invalid Kafka Msg");
            }
        } catch (Exception e) {
            logger.error(String.format("Error in Notification Consumer: Error Msg :%s", e.getMessage()), e);
        }
    }

    private void processNotificationRequest(String strData) {
        try {
            logger.info("Recevied data in notification consumer : {}", strData);
            Map<String, Object> workflowEventObj = mapper.readValue(strData, new TypeReference<Map<String, Object>>() {
            });
            String serviceName = (String) workflowEventObj.get(Constants.SERVICE_NAME);
            @SuppressWarnings("unchecked")
            List<WfRequest> wfRequests = mapper.convertValue(workflowEventObj.get(Constants.WORKFLOW_REQUESTS), new TypeReference<List<WfRequest>>() {
            });

            switch (serviceName) {
                case Constants.PROFILE_SERVICE_NAME:
                    wfRequests.forEach(wfRequest -> {
                        notificationService.sendNotification(wfRequest);
                        notificationService.sendNotificationToMdoAdmin(wfRequest);
                    });
                    break;
                case Constants.POSITION_SERVICE_NAME, Constants.DOMAIN_SERVICE_NAME, Constants.ORGANISATION_SERVICE_NAME:
                    wfRequests.forEach(notificationService::sendEmailNotification);
                    break;
                case Constants.BLENDED_PROGRAM_SERVICE_NAME, Constants.ONE_STEP_MDO_APPROVAL, Constants.ONE_STEP_PC_APPROVAL, Constants.TWO_STEP_MDO_AND_PC_APPROVAL, Constants.TWO_STEP_PC_AND_MDO_APPROVAL:
                    wfRequests.forEach(wfRequest -> {
                        Map<String, Object> courseAttributes = getCourseAttributes(wfRequest.getCourseId());
                        wfRequest.setCourseName((String) courseAttributes.get(Constants.COURSE_NAME));
                        notificationService.sendNotification(wfRequest);
                        notificationService.sendNotificationToMdoAdminAndPC(wfRequest);
                    });
                    break;
                case Constants.USER_REGISTRATION_SERVICE_NAME:
                    // nothing to do
                    break;
                default:
                    logger.error("Unsupported ServiceName in WFRequest.");
                    break;
            }
        } catch (Exception ex) {
            logger.error("Error while deserialization the object value", ex);
        }
    }

    public Map<String, Object> getCourseAttributes(String courseId) {
        return org.sunbird.workflow.utils.NotificationConsumerUtil.getCourseAttributes(cassandraOperation, configuration, courseId);
    }
}
