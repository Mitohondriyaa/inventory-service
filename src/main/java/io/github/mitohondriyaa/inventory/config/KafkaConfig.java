package io.github.mitohondriyaa.inventory.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConfig {
    @Bean
    public DefaultErrorHandler errorHandler() {
        FixedBackOff fixedBackOff = new FixedBackOff(1000, 3);

        return new DefaultErrorHandler(
            (record, exception) -> {
                log.error("Kafka error. Topic: {}, Key: {}, Value: {}, Exception: {}",
                    record.topic(),
                    record.key(),
                    record.value(),
                    exception.getMessage()
                );
            },
            fixedBackOff
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
        ConsumerFactory<String, Object> consumerFactory,
        DefaultErrorHandler errorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    @Bean
    public NewTopic inventoryReservedTopic() {
        return new NewTopic("inventory-reserved", 3, (short) 2);
    }

    @Bean
    public NewTopic inventoryRejectedTopic() {
        return new NewTopic("inventory-rejected", 3, (short) 2);
    }
}