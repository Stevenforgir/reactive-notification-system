package com.steven_udemy.notification_system.service;

import com.steven_udemy.notification_system.model.NotificationEvent;
import reactor.core.publisher.Mono;

public interface NotificationService {

    Mono<Boolean> sendNotification(NotificationEvent event);

}
