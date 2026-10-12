package org.sunbird.workflow.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Producer {

    Logger logger = LogManager.getLogger(Producer.class);

    private final ObjectMapper mapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void push(String topic, Object value) {
        String message = null;
        try {
            message = mapper.writeValueAsString(value);
            kafkaTemplate.send(topic, message);
        } catch (JsonProcessingException e) {
            logger.error("Exception while serializing the value", e);
        }
    }
}
