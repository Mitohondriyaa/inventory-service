package io.github.mitohondriyaa.inventory.config;

import jakarta.persistence.EntityNotFoundException;
import jakarta.xml.bind.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.hibernate.type.SerializationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
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
        DefaultErrorHandler errorHandler =  new DefaultErrorHandler(
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

        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            DataIntegrityViolationException.class,
            EntityNotFoundException.class,
            IllegalStateException.class,
            ValidationException.class,
            SerializationException.class
        );

        return errorHandler;
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
    @Profile("!test")
    public NewTopic inventoryReservedTopic() {
        return new NewTopic("inventory-reserved", 3, (short) 2);
    }

    @Bean
    @Profile("!test")
    public NewTopic inventoryRejectedTopic() {
        return new NewTopic("inventory-rejected", 3, (short) 2);
    }
}