package com.steven_udemy.notification_system.service;

import com.steven_udemy.notification_system.model.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class TeamsService implements NotificationService {

    public Mono<Boolean> sendNotification(NotificationEvent event) {
       return Mono.fromCallable(() -> { //Simulate sending notification - fromcallable es para operaciones bloqueantes
           Thread.sleep(150);

           //Simulate error with 10% probability
           if(ThreadLocalRandom.current().nextInt(10) == 0){
               throw new RuntimeException("Error sending Teams notification");
           }
           log.info("Msg sent to Teams: {}", event);
           return true;
       });
    }
}
