package org.sunbird.workflow.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.sunbird.workflow.models.WfRequest;
import org.sunbird.workflow.service.impl.ApplicationProcessingServiceImpl;
import org.sunbird.workflow.service.impl.WorkflowAuditProcessingServiceImpl;
import org.sunbird.workflow.service.impl.WorkflowESSyncServiceImpl;
import org.sunbird.workflow.utils.ApplicationProcessingConsumerUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ApplicationProcessingConsumer {

    Logger logger = LogManager.getLogger(ApplicationProcessingConsumer.class);

    private final ObjectMapper mapper;
    private final ApplicationProcessingServiceImpl applicationProcessingServiceImpl;
    private final WorkflowAuditProcessingServiceImpl workflowAuditProcessingService;
    private final WorkflowESSyncServiceImpl workflowESSyncServiceImpl;

    public ApplicationProcessingConsumer(ObjectMapper mapper,
            @Lazy ApplicationProcessingServiceImpl applicationProcessingServiceImpl,
            WorkflowAuditProcessingServiceImpl workflowAuditProcessingService,
            WorkflowESSyncServiceImpl workflowESSyncServiceImpl) {
        this.mapper = mapper;
        this.applicationProcessingServiceImpl = applicationProcessingServiceImpl;
        this.workflowAuditProcessingService = workflowAuditProcessingService;
        this.workflowESSyncServiceImpl = workflowESSyncServiceImpl;
    }


    @KafkaListener(groupId = "workflowContentTopic-consumer", topics = "${kafka.topics.workflow.request}")
    public void processMessage(ConsumerRecord<String, String> data) {
        ApplicationProcessingConsumerUtil.processMessage(data, logger, this::processApplicationMessage);
    }

    private void processApplicationMessage(String strData) {
        WfRequest wfRequest = null;
        try {
            wfRequest = mapper.readValue(strData, WfRequest.class);
            if (logger.isInfoEnabled()) {
                logger.info("Recevied data in user profile consumer : {}", mapper.writeValueAsString(wfRequest));
            }
        } catch (Exception ex) {
            logger.error("Error while deserialization the object value", ex);
        }
        applicationProcessingServiceImpl.processWfApplicationRequest(wfRequest);
        workflowAuditProcessingService.createAudit(wfRequest);
        applicationProcessingServiceImpl.updateDepartmentToPortalDBs(wfRequest);
        workflowESSyncServiceImpl.syncWithElasticService(wfRequest);
    }
}


