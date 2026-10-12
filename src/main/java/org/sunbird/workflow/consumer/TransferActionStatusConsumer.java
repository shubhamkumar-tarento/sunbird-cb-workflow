package org.sunbird.workflow.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.sunbird.workflow.postgres.entity.WfStatusEntity;
import org.sunbird.workflow.postgres.repo.WfStatusRepo;
import org.sunbird.workflow.utils.CassandraOperation;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransferActionStatusConsumer {

    private static final Logger logger = LogManager.getLogger(TransferActionStatusConsumer.class);

    private final ObjectMapper mapper;
    private final WfStatusRepo wfStatusRepo;
    private final CassandraOperation cassandraOperation;

    @KafkaListener(topics = "${kafka.topic.transfer.request.status.change}", groupId = "transfer-status-consumer-group")
    public void processStatusChange(ConsumerRecord<String, String> data) {
        try {
            if (StringUtils.isNotBlank(data.value())) {
                CompletableFuture.runAsync(() -> handleTransferStatusChange(data.value()));
            } else {
                logger.error("Empty message received on transfer_request_status_change topic.");
            }
        } catch (Exception e) {
            logger.error("Error while processing transfer request status change: {}", e.getMessage(), e);
        }
    }

    private void handleTransferStatusChange(String payload) {
        try {
            logger.info("Received transfer request status payload: {}", payload);

            JsonNode jsonNode = mapper.readTree(payload);
            String state = jsonNode.get("orgTransferState").asText();
            Boolean inworkflow = jsonNode.get("inWorkflow").asBoolean();
            JsonNode statusListNode = jsonNode.get("groupDesignationEntities");

            logger.info("Processing {} groupDesignationEntities | State: '{}' | InWorkflow: {}",
                    statusListNode.size(), state, inworkflow);

            for (JsonNode node : statusListNode) {
                WfStatusEntity entity = mapper.treeToValue(node, WfStatusEntity.class);
                logger.info("Processing entity with wfId: {}", entity.getWfId());
                entity.setCurrentStatus(state);
                entity.setInWorkflow(inworkflow);
                entity.setLastUpdatedOn(new Date());
                wfStatusRepo.save(entity);
                logger.info("Successfully updated entity with wfId: {}", entity.getWfId());
            }
            logger.info("Completed processing all groupDesignationEntities.");
        } catch (IOException e) {
            logger.error("Failed to parse transfer request status payload", e);
        } catch (Exception e) {
            logger.error("Unexpected error processing transfer request status change", e);
        }
    }
}
