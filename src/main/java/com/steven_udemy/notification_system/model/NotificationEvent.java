package com.steven_udemy.notification_system.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
@Data
public class NotificationEvent {

    private String id;
    private String source;
    private String message;
    private Priority priority;
    private LocalDateTime timestamp;
    private NotificationStatus status;
}
