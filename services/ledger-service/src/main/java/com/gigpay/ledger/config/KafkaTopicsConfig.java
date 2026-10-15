package com.gigpay.ledger.config;

import com.gigpay.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Every service declares every topic it touches, with identical partition counts (auto-create is off). */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic earningsAccrued() {
        return TopicBuilder.name(Topics.EARNINGS_ACCRUED).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic payoutEvents() {
        return TopicBuilder.name(Topics.PAYOUT_EVENTS).partitions(6).replicas(1).build();
    }
}
