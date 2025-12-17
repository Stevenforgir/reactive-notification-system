package com.steven_udemy.notification_system;

import com.steven_udemy.notification_system.model.NotificationEvent;
import com.steven_udemy.notification_system.model.NotificationStatus;
import com.steven_udemy.notification_system.model.Priority;
import com.steven_udemy.notification_system.service.EmailService;
import com.steven_udemy.notification_system.service.NotificationService;
import com.steven_udemy.notification_system.service.PhoneService;
import com.steven_udemy.notification_system.service.TeamsService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class NotificationSystem {

    private final Sinks.Many<NotificationEvent> mainEventSink;

    @Getter
    private final Sinks.Many<NotificationEvent> historySink;

    private final NotificationService teamsService;

    private final NotificationService emailService;

    private final NotificationService phoneService;

    private final Sinks.One<NotificationEvent> teamsSink;
    private final Sinks.One<NotificationEvent> emailSink;
    private final Sinks.One<NotificationEvent> phoneSink;

    private final ConcurrentMap<String, NotificationEvent> notificationCache;

    private static final String TEAMS_CHANNEL = "Teams";
    private static final String EMAIL_CHANNEL = "Email";
    private static final String PHONE_CHANNEL = "Phone";

    public NotificationSystem() {
        this.mainEventSink = Sinks.many().multicast().onBackpressureBuffer(); // el OnbackpressureBuffer es para que no se pierdan eventos si el suscriptor no puede seguir el ritmo del publicador, entonces guarda los eventos en un buffer
        this.historySink = Sinks.many().replay().limit(50); // guarda los ultimos 50 eventos para que los nuevos suscriptores puedan verlos

        this.teamsSink = Sinks.one(); //suscriptor unico para cada servicio de notificacion
        this.emailSink = Sinks.one();
        this.phoneSink = Sinks.one();

        this.phoneService = new PhoneService();
        this.teamsService = new TeamsService();
        this.emailService = new EmailService();

        this.notificationCache = new ConcurrentHashMap<>();

        this.setupProcessingFlows();
    }

    public NotificationSystem(NotificationService teamsService,
                              NotificationService emailService,
                              NotificationService phoneService) {
        this.mainEventSink = Sinks.many().multicast().onBackpressureBuffer(); // el OnbackpressureBuffer es para que no se pierdan eventos si el suscriptor no puede seguir el ritmo del publicador, entonces guarda los eventos en un buffer
        this.historySink = Sinks.many().replay().limit(50); // guarda los ultimos 50 eventos para que los nuevos suscriptores puedan verlos

        this.teamsSink = Sinks.one(); //suscriptor unico para cada servicio de notificacion
        this.emailSink = Sinks.one();
        this.phoneSink = Sinks.one();

        this.phoneService = phoneService;
        this.teamsService = teamsService;
        this.emailService = emailService;

        this.notificationCache = new ConcurrentHashMap<>();

        this.setupProcessingFlows();
    }

    private void setupProcessingFlows(){
    //Procesador principal que enruta los eventos a los diferentes canales}
        this.mainEventSink
                .asFlux()
                .doOnNext(this::updateEventStatus) // se actualiza el estatus del evento a PENDING
                .doOnNext(event -> log.info("Routing event: {}", event))
                .doOnNext(this.historySink::tryEmitNext) // se guarda el evento en el historial
                .subscribe(this::routeEventByPriority);

        //Procesadores para cada canal de notificacion
        this.setupTeamsProcessor();
        this.setupEmailProcessor();
        this.setupPhoneProcessor();
    }

    private void setupTeamsProcessor(){
        this.teamsSink
                .asMono()//Se convierte el sink a un mono para procesar un solo evento
                .flatMap(event ->
                        this.teamsService.sendNotification(event)
                            .subscribeOn(Schedulers.boundedElastic())//Explicame esto
                            .doOnSuccess(v -> this.updateSuccess(event, TEAMS_CHANNEL))
                            .doOnError(error -> this.updateErrorStatus(event, TEAMS_CHANNEL, error))
                            .onErrorResume(error -> Mono.just(false))
                )
                .subscribe();
    }

    private void setupEmailProcessor(){
        this.emailSink
                .asMono()//Se convierte el sink a un mono para procesar un solo evento
                .flatMap(event ->
                        this.emailService.sendNotification(event)
                                .subscribeOn(Schedulers.boundedElastic())//Dice con que hilo me quiero suscribir, el parametro es el pool de threads
                                .doOnSuccess(v -> this.updateSuccess(event, EMAIL_CHANNEL))
                                .doOnError(error -> this.updateErrorStatus(event, EMAIL_CHANNEL, error))
                                .onErrorResume(error -> Mono.just(false))
                )
                .subscribe();
    }

    private void setupPhoneProcessor(){
        this.phoneSink
                .asMono()//Se convierte el sink a un mono para procesar un solo evento
                .flatMap(event ->
                        this.phoneService.sendNotification(event)
                                .subscribeOn(Schedulers.boundedElastic())//Dice con que hilo me quiero suscribir, el parametro es el pool de threads
                                .doOnSuccess(v -> this.updateSuccess(event, PHONE_CHANNEL))
                                .doOnError(error -> this.updateErrorStatus(event, PHONE_CHANNEL, error))
                                .retry(3) // reintenta 3 veces en caso de error
                                .onErrorResume(error -> Mono.just(false))
                )
                .subscribe();
    }

    /**
     * Por si se quiere actualizar en otro metodo el estatus del evento
     * @param event
     */
    private void updateEventStatus(NotificationEvent event) {
        if(Objects.isNull(event.getId())){
            event.setId(UUID.randomUUID().toString());
        }
        if(Objects.isNull(event.getStatus())){
            event.setStatus(NotificationStatus.PENDING);
        }

        this.notificationCache.put(event.getId(), event);
    }

    /**
     * En caso de que la notificacion sea exitosa se actualiza el status a DELIVERED
     * @param event
     * @param channel
     */
    private void updateSuccess(NotificationEvent event, String channel){
        log.info("Success event by: {}, event: {}", channel, event);
        NotificationEvent cacheEvent = this.notificationCache.get(event.getId());
        if(Objects.nonNull(cacheEvent)){
            cacheEvent.setStatus(NotificationStatus.DELIVERED);
            this.historySink.tryEmitNext(cacheEvent);
        }
    }

    /**
     * En caso de que falle la notificacion se actualiza el status a FAILED
     * @param event
     * @param channel
     * @param error
     */
    private void updateErrorStatus(NotificationEvent event, String channel, Throwable error){
        log.error("Error sending notification via {}: for event: {}, error: {}", channel, event, error.getMessage());
        NotificationEvent cacheEvent = this.notificationCache.get(event.getId());

        if(Objects.nonNull(cacheEvent)){
            cacheEvent.setStatus(NotificationStatus.FAILED);
            this.historySink.tryEmitNext(cacheEvent);
        }
    }

    private void routeEventByPriority(NotificationEvent event){
        this.teamsSink.tryEmitValue(event);
        if(event.getPriority() == Priority.HIGH || event.getPriority() == Priority.MEDIUM){
            this.emailSink.tryEmitValue(event);
        }
        if(event.getPriority() == Priority.HIGH){
            this.phoneSink.tryEmitValue(event);
        }
    }

    public void publishEvent(NotificationEvent event){
        this.mainEventSink.tryEmitNext(event);
    }

    public Flux<NotificationEvent> getNotificationHistory(){
        return this.historySink.asFlux();
    }

    public Mono<NotificationEvent> getNotificationByIdFromCache(String id){
        return Mono.justOrEmpty(this.notificationCache.get(id));
    }

    public Flux<NotificationEvent> retryFailedNotification() {
        return Flux.fromIterable(this.notificationCache.values())
                .filter(event -> event.getStatus() == NotificationStatus.FAILED)
                .doOnNext(this::publishEvent);
    }

}
