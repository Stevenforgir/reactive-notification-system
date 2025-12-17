package com.steven_udemy.notification_system.model;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
public class NotificationEvent {

    private String source;
    private String message;
    private Priority priority;
    private LocalDateTime timestamp;
}
