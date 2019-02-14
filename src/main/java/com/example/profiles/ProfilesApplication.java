package com.example.profiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

@SpringBootApplication
public class ProfilesApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProfilesApplication.class, args);
	}

}

/// glue
@Component
class ProfileEventPublisher implements
	Consumer<FluxSink<ProfileCreatedEvent>>,
	ApplicationListener<ProfileCreatedEvent> {

	private final BlockingQueue<ProfileCreatedEvent> events = new LinkedBlockingQueue<>();
	private final Executor executor;

	ProfileEventPublisher(Executor executor) {
		this.executor = executor;
	}

	@SneakyThrows
	private void send(FluxSink<ProfileCreatedEvent> pce) {
		pce.next(events.take());
	}

	@Override
	public void accept(FluxSink<ProfileCreatedEvent> sink) {
		this.executor.execute(() -> this.send(sink));
	}

	@Override
	public void onApplicationEvent(ProfileCreatedEvent event) {
		this.events.offer(event);
	}
}

/// web
@Configuration
class WebSocketConfiguration {

	private final ObjectMapper objectMapper;
	private final ProfileEventPublisher publisher;

	WebSocketConfiguration(ObjectMapper objectMapper, ProfileEventPublisher publisher) {
		this.objectMapper = objectMapper;
		this.publisher = publisher;
	}

	@Bean
	WebSocketHandlerAdapter webSocketHandlerAdapter() {
		return new WebSocketHandlerAdapter();
	}

	@SneakyThrows
	private String from(ProfileCreatedEvent pce) {
		return objectMapper.writeValueAsString(pce);
	}

	@Bean
	WebSocketHandler wsh() {
		return session -> {
			Flux<ProfileCreatedEvent> publish = Flux
				.create(this.publisher)
				.share();
			Flux<WebSocketMessage> map = publish
				.map(this::from)
				.map(session::textMessage);
			return session.send(map);
		};
	}

	@Bean
	HandlerMapping handlerMapping() {
		return new SimpleUrlHandlerMapping() {
			{
				setUrlMap(Collections.singletonMap("/ws/profiles", wsh()));
				setOrder(10);
			}
		};
	}

}

@RestController
@RequestMapping("/profiles")
class ProfileRestController {

	private final ProfileService profileService;

	ProfileRestController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@GetMapping
	Flux<Profile> all() {
		return this.profileService.all();
	}

	@GetMapping("/{id}")
	Mono<Profile> byId(String id) {
		return this.profileService.byId(id);
	}

	@PostMapping
	Mono<ResponseEntity<Profile>> create(@RequestBody Profile profile) {
		return this.profileService
			.create(profile.getEmail())
			.map(p -> ResponseEntity.created(URI.create("/profiles/" + p.getId())).build());
	}

}

@RestController
class ProfileSseController {

	private final ProfileEventPublisher publisher;

	ProfileSseController(ProfileEventPublisher publisher) {
		this.publisher = publisher;
	}

	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE, value = "/sse/profiles")
	Flux<ProfileCreatedEvent> sse() {
		return Flux.create(this.publisher).share();
	}

}


/// basics

class ProfileCreatedEvent extends ApplicationEvent {

	ProfileCreatedEvent(Profile profile) {
		super(profile);
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

	Mono<Profile> byId(String id) {
		return this.profileRepository.findById(id);
	}

	Mono<Profile> create(String email) {
		return this.profileRepository
			.save(new Profile(null, email))
			.doOnSuccess(profile -> publisher.publishEvent(new ProfileCreatedEvent(profile)));
	}

	Flux<Profile> all() {
		return this.profileRepository.findAll();
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