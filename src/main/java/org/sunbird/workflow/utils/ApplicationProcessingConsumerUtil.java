package org.sunbird.workflow.utils;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.Logger;

public final class ApplicationProcessingConsumerUtil {

    private ApplicationProcessingConsumerUtil() {
    }

    public static void processMessage(ConsumerRecord<String, String> data, Logger logger,
            Consumer<String> processApplicationMessage) {
        try {
            if (StringUtils.isNoneBlank(data.value())) {
                CompletableFuture.runAsync(() -> processApplicationMessage.accept(data.value()));
            } else {
                logger.error("Error in Application Consumer: Invalid Kafka Msg");
            }
        } catch (Exception e) {
            logger.error(String.format("Error in Application Consumer: Error Msg :%s", e.getMessage()), e);
        }
    }
}
