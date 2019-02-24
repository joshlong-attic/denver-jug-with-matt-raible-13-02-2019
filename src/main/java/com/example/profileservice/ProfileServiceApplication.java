package com.example.profileservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

@SpringBootApplication
public class ProfileServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProfileServiceApplication.class, args);
	}

}

class ProfileCreatedEvent extends ApplicationEvent {

	public ProfileCreatedEvent(Profile source) {
		super(source);
	}
}

@Service
class ProfileService {


	private final ProfileRepository profileRepository;
	private final ApplicationEventPublisher publisher;

	ProfileService(ProfileRepository profileRepository, ApplicationEventPublisher publisher) {
		this.profileRepository = profileRepository;
		this.publisher = publisher;
	}

	Mono<Profile> create(String email) {
		return this.profileRepository
			.save(new Profile(null, email))
			.doOnSuccess(profile -> this.publisher.publishEvent(new ProfileCreatedEvent(profile)));
	}

	Flux<Profile> all() {
		return this.profileRepository.findAll();
	}

	Mono<Profile> byId(String id) {
		return this.profileRepository.findById(id);
	}


}


@Configuration
class WebsocketConfiguration {


	WebsocketConfiguration(ObjectMapper objectMapper, ProfileCreatedEventPublisher profileCreatedEventPublisher) {
		this.objectMapper = objectMapper;
		this.profileCreatedEventPublisher = profileCreatedEventPublisher;
	}

	@Bean
	WebSocketHandlerAdapter webSocketHandlerAdapter() {
		return new WebSocketHandlerAdapter();
	}

	@Bean
	HandlerMapping handlerMapping() {
		return new SimpleUrlHandlerMapping() {
			{
				setOrder(10);
				setUrlMap(Collections.singletonMap("/ws/profiles", handler()));
			}
		};
	}

	@SneakyThrows
	private String jsonFrom(ProfileCreatedEvent pce) {
		Map<String, String> data = new HashMap<>();
		Profile profile = (Profile) pce.getSource();
		data.put("id", profile.getId());
		return objectMapper.writeValueAsString(data);
	}

	private final ObjectMapper objectMapper;
	private final ProfileCreatedEventPublisher profileCreatedEventPublisher;

	@Bean
	WebSocketHandler handler() {

		Flux<ProfileCreatedEvent> share = Flux.create(profileCreatedEventPublisher).share();

		return session -> {
			Flux<WebSocketMessage> map = share
				.map(this::jsonFrom)
				.map(session::textMessage);

			return session.send(map);
		};
	}

}


@RestController
class SseController {

	private final ProfileCreatedEventPublisher publisher;
	private final Flux<ProfileCreatedEvent> eventFlux;

	SseController(ProfileCreatedEventPublisher publisher, ObjectMapper objectMapper) {
		this.publisher = publisher;
		this.eventFlux = Flux.create(this.publisher).share();
	}

	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE, value = "/sse/profiles")
	Flux<ProfileCreatedEvent> sse() {
		return this.eventFlux;
	}
}

@Component
class ProfileCreatedEventPublisher
	implements ApplicationListener<ProfileCreatedEvent>,
	Consumer<FluxSink<ProfileCreatedEvent>> {

	private final BlockingQueue<ProfileCreatedEvent> events =
		new LinkedBlockingQueue<>();

	private final Executor executor;

	ProfileCreatedEventPublisher(Executor executor) {
		this.executor = executor;
	}

	@Override
	public void accept(FluxSink<ProfileCreatedEvent> profileCreatedEventFluxSink) {

		this.executor.execute(() -> {

			while (true) {
				try {
					profileCreatedEventFluxSink.next(events.take());
				}
				catch (InterruptedException e) {
					ReflectionUtils.rethrowRuntimeException(e);
				}
			}
		});

	}

	@Override
	public void onApplicationEvent(ProfileCreatedEvent profileCreatedEvent) {
		this.events.offer(profileCreatedEvent);
	}
}

@RestController
@RequestMapping("/profiles")
class ProfileRestController {

	private final ProfileService service;

	ProfileRestController(ProfileService service) {
		this.service = service;
	}

	@PostMapping
	Publisher<ResponseEntity<Profile>> create(@RequestBody Profile profile) {
		return
			this.service.create(profile.getEmail())
				.map(savedProfile -> ResponseEntity
					.created(URI.create("/profiles/" + savedProfile.getId()))
					.build()
				);
	}

	@GetMapping("/{id}")
	Publisher<Profile> byId(@PathVariable String id) {
		return this.service.byId(id);
	}

	@GetMapping
	Publisher<Profile> all() {
		return this.service.all();
	}

}

interface ProfileRepository extends ReactiveMongoRepository<Profile, String> {
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document
class Profile {

	@Id
	private String id;

	private String email;
}

@Log4j2
@Component
@org.springframework.context.annotation.Profile("demo")
class SampleDataInitializer
		implements ApplicationListener<ApplicationReadyEvent> {

	private final ProfileRepository repository;

	public SampleDataInitializer(ProfileRepository repository) {
		this.repository = repository;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		repository
				.deleteAll()
				.thenMany(
						Flux
								.just("A", "B", "C", "D")
								.map(name -> new Profile(UUID.randomUUID().toString(), name + "@email.com"))
								.flatMap(repository::save)
				)
				.thenMany(repository.findAll())
				.subscribe(profile -> log.info("saving " + profile.toString()));
	}
}
