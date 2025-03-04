package com.telecom.cqrs.query.config;

import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.telecom.cqrs.query.event.PhonePlanEventHandler;
import com.telecom.cqrs.query.event.UsageEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
public class EventHubConfig {
    @Value("${EVENT_HUB_PLAN_CONNECTION_STRING}")
    private String planConnectionString;

    @Value("${EVENT_HUB_USAGE_CONNECTION_STRING}")
    private String usageConnectionString;

    @Value("${EVENT_HUB_PLAN_NAME}")
    private String planHubName;

    @Value("${EVENT_HUB_USAGE_NAME}")
    private String usageHubName;

    @Value("${BLOB_CONTAINER}")
    private String blobContainer;

    private final BlobStorageConfig blobStorageConfig;
    private final UsageEventHandler usageEventHandler;
    private final PhonePlanEventHandler planEventHandler;

    public EventHubConfig(
            BlobStorageConfig blobStorageConfig,
            UsageEventHandler usageEventHandler,
            PhonePlanEventHandler planEventHandler) {
        this.blobStorageConfig = blobStorageConfig;
        this.usageEventHandler = usageEventHandler;
        this.planEventHandler = planEventHandler;
    }

    @PostConstruct
    public void validateConfig() {
        log.info("Validating Event Hub configuration...");
        validateConnectionString("planConnectionString", planConnectionString);
        validateConnectionString("usageConnectionString", usageConnectionString);
        validateNotEmpty("planHubName", planHubName);
        validateNotEmpty("usageHubName", usageHubName);
        validateNotEmpty("blobContainer", blobContainer);
        log.info("Event Hub configuration validated successfully");
    }

    private void validateConnectionString(String name, String connectionString) {
        if (connectionString == null || connectionString.trim().isEmpty()
                || !connectionString.contains("Endpoint=")
                || !connectionString.contains("SharedAccessKeyName=")) {
            throw new IllegalStateException(
                    String.format("Invalid Event Hub connection string format for %s", name));
        }
    }

    private void validateNotEmpty(String name, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                    String.format("Event Hub configuration error: %s is not configured", name));
        }
    }

    @Bean
    public EventProcessorClient usageEventProcessor() {
        log.info("Creating usage event processor with hub: {}, container: {}",
                usageHubName, blobContainer);

        var blobClient = blobStorageConfig
                .getBlobContainerAsyncClient(blobContainer);

        EventProcessorClient client = new EventProcessorClientBuilder()
                .connectionString(usageConnectionString, usageHubName)
                .consumerGroup("$Default")
                .checkpointStore(new BlobCheckpointStore(blobClient))
                .processEvent(usageEventHandler)
                .processError(usageEventHandler::processError)
                .buildEventProcessorClient();

        usageEventHandler.setEventProcessorClient(client);
        return client;
    }

    @Bean
    public EventProcessorClient planEventProcessor() {
        log.info("Creating plan event processor with hub: {}, container: {}",
                planHubName, blobContainer);

        var blobClient = blobStorageConfig
                .getBlobContainerAsyncClient(blobContainer);

        EventProcessorClient client = new EventProcessorClientBuilder()
                .connectionString(planConnectionString, planHubName)
                .consumerGroup("$Default")
                .checkpointStore(new BlobCheckpointStore(blobClient))
                .processEvent(planEventHandler)
                .processError(planEventHandler::processError)
                .buildEventProcessorClient();

        planEventHandler.setEventProcessorClient(client);
        return client;
    }
}
