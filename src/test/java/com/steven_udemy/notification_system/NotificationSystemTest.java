package com.steven_udemy.notification_system;

import com.steven_udemy.notification_system.model.NotificationEvent;
import com.steven_udemy.notification_system.model.NotificationStatus;
import com.steven_udemy.notification_system.model.Priority;
import com.steven_udemy.notification_system.service.EmailService;
import com.steven_udemy.notification_system.service.NotificationService;
import com.steven_udemy.notification_system.service.PhoneService;
import com.steven_udemy.notification_system.service.TeamsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class NotificationSystemTest {

    private NotificationService mockTeamsService;
    private NotificationService mockEmailService;
    private NotificationService mockPhoneService;

    private NotificationSystem notificationSystem;

    private AtomicInteger teamsCallCount;
    private AtomicInteger emailCallCount;
    private AtomicInteger phoneCallCount;

    @BeforeEach
    void setup(){
        this.teamsCallCount = new AtomicInteger(0);
        this.emailCallCount = new AtomicInteger(0);
        this.phoneCallCount = new AtomicInteger(0);

        this.mockTeamsService = mock(TeamsService.class);
        this.mockEmailService = mock(EmailService.class);
        this.mockPhoneService = mock(PhoneService.class);

        when(this.mockPhoneService.sendNotification(any(NotificationEvent.class)))
                .thenAnswer(invocation -> {
                    this.phoneCallCount.incrementAndGet();
                    return Mono.just(true);
                });

        when(this.mockTeamsService.sendNotification(any(NotificationEvent.class)))
                .thenAnswer(invocation -> {
                    this.teamsCallCount.incrementAndGet();
                    return Mono.just(true);
                });

        when(this.mockEmailService.sendNotification(any(NotificationEvent.class)))
                .thenAnswer(invocation -> {
                    this.emailCallCount.incrementAndGet();
                    return Mono.just(true);
                });

        this.notificationSystem = new NotificationSystem(
                mockTeamsService, mockEmailService, mockPhoneService
        );
    }

    @Test
    @DisplayName("Should send events with LOW priority")
    void testLowPriorityNotifications() {
        NotificationEvent event = createTestEvent(Priority.LOW);
        this.notificationSystem.publishEvent(event);

        this.sleep(500); // Wait for async processing

        verify(mockTeamsService, times(1)).sendNotification(any());
        verify(mockEmailService, never()).sendNotification(any());
        verify(mockPhoneService, never()).sendNotification(any());

        assertEquals(1, teamsCallCount.get());
        assertEquals(0, emailCallCount.get());
        assertEquals(0, phoneCallCount.get());
    }

    private NotificationEvent createTestEvent(Priority priority) {
        return NotificationEvent.builder()
                .id(UUID.randomUUID().toString())
                .source("TEST")
                .message("Test msg with priority: " + priority.toString())
                .priority(priority)
                .timestamp(LocalDateTime.now())
                .status(NotificationStatus.PENDING)
                .build();
    }
    private void sleep(long mills) {
        try {
            Thread.sleep(mills);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


}
