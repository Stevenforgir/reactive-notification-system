package com.steven_udemy.notification_system.service;

import com.steven_udemy.notification_system.model.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class EmailService implements NotificationService {

    public Mono<Boolean> sendNotification(NotificationEvent event) {
        return Mono.fromCallable(() -> { //Simulate sending notification - fromcallable es para operaciones bloqueantes
            Thread.sleep(300);

            //Simulate error with 15% probability
            if(ThreadLocalRandom.current().nextInt(100) <= 15){
                throw new RuntimeException("Error sending Email notification");
            }
            log.info("Msg sent to Email: {}", event);
            return true;
        });
    }
}
