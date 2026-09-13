package com.jobpilot.event;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableKafka
@EnableConfigurationProperties({OutboxProperties.class, KafkaConsumerProperties.class})
public class EventModuleConfiguration {
}
