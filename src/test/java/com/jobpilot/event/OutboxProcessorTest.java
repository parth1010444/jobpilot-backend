package com.jobpilot.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxProperties properties;
    private OutboxProcessor processor;

    @BeforeEach
    void setUp() {
        properties = new OutboxProperties();
        properties.getRelay().setMaxAttempts(3);
        properties.getRelay().setBatchSize(10);
        processor = new OutboxProcessor(outboxEventRepository, properties, kafkaTemplate, CLOCK);
    }

    @Test
    void successfulPublishMarksPublished() {
        OutboxEvent event = pendingEvent();
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("jobpilot.application.events"), eq(event.getPartitionKey()), eq(event.getPayload())))
                .thenReturn(future);
        when(outboxEventRepository.markPublished(event.getId(), NOW)).thenReturn(1);

        boolean ok = processor.processOne(event);

        assertThat(ok).isTrue();
        verify(outboxEventRepository).markPublished(event.getId(), NOW);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void sendFailureIncrementsAttemptsAndLeavesPending() {
        OutboxEvent event = pendingEvent();
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean ok = processor.processOne(event);

        assertThat(ok).isFalse();
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(saved.getLastError()).contains("broker down");
        verify(outboxEventRepository, never()).markPublished(any(), any());
    }

    @Test
    void sendFailureMarksFailedAfterMaxAttempts() {
        OutboxEvent event = pendingEvent();
        // Simulate already at maxAttempts - 1
        event.recordFailure("previous", 3);
        event.recordFailure("previous", 3);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("still down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean ok = processor.processOne(event);

        assertThat(ok).isFalse();
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAttempts()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }

    @Test
    void markPublishedReturningZeroIsNotCountedAsSuccess() {
        OutboxEvent event = pendingEvent();
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(outboxEventRepository.markPublished(event.getId(), NOW)).thenReturn(0);

        boolean ok = processor.processOne(event);

        assertThat(ok).isFalse();
    }

    private static OutboxEvent pendingEvent() {
        return new OutboxEvent(
                "APPLICATION",
                UUID.randomUUID(),
                "APPLICATION_STATUS_CHANGED",
                "{\"applicationId\":\"x\"}",
                "jobpilot.application.events",
                UUID.randomUUID().toString(),
                NOW
        );
    }
}
