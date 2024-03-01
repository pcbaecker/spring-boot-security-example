package com.pcbaecker.integration;

import com.pcbaecker.config.security.SessionRestController;
import com.pcbaecker.domain.users.User;
import com.pcbaecker.domain.users.UserRepository;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class SessionIT {

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebTestClient client;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PostgreSQLContainer.IMAGE);

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(
            RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG));

    @Test
    void test_login() {
        createUserIfNotExists("user", "password", List.of());
        // GIVEN
        try (var jedisPool = new JedisPool(redis.getHost(), redis.getMappedPort(RedisContainer.REDIS_PORT))) {
            Jedis jedis = jedisPool.getResource();
            jedis.flushAll();
            assertThat(jedis.keys("*").size()).isZero();

            // WHEN
            AtomicReference<String> sessionCookie = new AtomicReference<>();
            client
                    .post()
                    .uri("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("User-Agent", "Test")
                    .body(BodyInserters.fromValue(new SessionRestController.LoginRequest("user", "password")))
                    .exchange()

                    // THEN
                    .expectStatus().isOk()
                    .expectCookie().value("SESSION", sessionCookie::set);
            assertThat(jedis.keys("*").size()).isPositive();
            assertThat(this.sessionRepo.findByPrincipalName("user").size()).isOne();
        }
    }

    @Test
    void test_logout() {
        createUserIfNotExists("user", "password", List.of());
        // GIVEN
        try (var jedisPool = new JedisPool(redis.getHost(), redis.getMappedPort(RedisContainer.REDIS_PORT))) {
            Jedis jedis = jedisPool.getResource();
            jedis.flushAll();
            assertThat(jedis.keys("*").size()).isZero();
            AtomicReference<String> sessionCookie = new AtomicReference<>();
            client.post()
                    .uri("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(new SessionRestController.LoginRequest("user", "password")))
                    .exchange()
                    .expectStatus().isOk()
                    .expectCookie().value("SESSION", sessionCookie::set)
                    .expectCookie();
            assertThat(jedis.keys("*").size()).isPositive();

            // WHEN
            client.post()
                    .uri("/logout")
                    .cookie("SESSION", sessionCookie.get())
                    .exchange()

                    // THEN
                    .expectStatus().isOk();
            // TODO Why are there still keys in the Redis database?
            /*
            System.err.println("--------------------");
            jedis.keys("*").forEach(k -> {
                System.err.println(k + " -> ");
                switch (jedis.type(k)) {
                    case "hash" -> System.err.println(jedis.hgetAll(k));
                    case "string" -> System.err.println(jedis.get(k));
                    case "list" -> System.err.println(jedis.lrange(k, 0, -1));
                    case "set" -> System.err.println(jedis.smembers(k));
                    case "zset" -> System.err.println(jedis.zrangeWithScores(k, 0, -1));
                }
            });
            System.err.println(sessions.findById(sessionCookie.get()));
            assertThat(jedis.keys("*").size()).isZero();*/
        }
    }

    @Test
    void test_getMySessions() {
        createUserIfNotExists("user", "password", List.of());
        for (int i = 1; i <= 3; i++) {
            // GIVEN
            final int finalTestIteration = i;
            AtomicReference<String> sessionCookie = new AtomicReference<>();
            client
                    .post()
                    .uri("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(new SessionRestController.LoginRequest("user", "password")))
                    .exchange()
                    .expectStatus().isOk()
                    .expectCookie().value("SESSION", sessionCookie::set);

            // WHEN
            client
                    .get()
                    .uri("/sessions")
                    .cookie("SESSION", sessionCookie.get())
                    .exchange()

                    // THEN
                    .expectStatus().isOk()
                    .expectBodyList(SessionRestController.SessionInfo.class)
                    .consumeWith(response -> {
                        var sessions = response.getResponseBody();
                        assertThat(sessions).isNotNull();
                        assertThat(sessions).isNotEmpty();
                        assertThat(sessions).size().isGreaterThanOrEqualTo(finalTestIteration);
                        assertThat(sessions.stream().filter(SessionRestController.SessionInfo::isCurrent).count()).isPositive();
                        sessions.forEach(s -> assertThat(s.userAgent()).isNotEmpty());
                    });
        }
    }

    @Test
    void test_deleteSession() {
        createUserIfNotExists("user", "password", List.of());
        // GIVEN
        client
                .post()
                .uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new SessionRestController.LoginRequest("user", "password")))
                .exchange()
                .expectStatus().isOk();
        AtomicReference<String> sessionCookie = new AtomicReference<>();
        client
                .post()
                .uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new SessionRestController.LoginRequest("user", "password")))
                .exchange()
                .expectStatus().isOk()
                .expectCookie().value("SESSION", sessionCookie::set);
        client
                .get()
                .uri("/sessions")
                .cookie("SESSION", sessionCookie.get())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(SessionRestController.SessionInfo.class)
                .consumeWith(response -> {
                    var sessions = response.getResponseBody();
                    assertThat(sessions).isNotNull();
                    assertThat(sessions).isNotEmpty();
                    assertThat(sessions).size().isGreaterThanOrEqualTo(2);
                    var currentSession = sessions.stream().filter(s -> !s.isCurrent()).findFirst().orElseThrow();

                    // WHEN
                    client
                            .delete()
                            .uri("/sessions/" + currentSession.id())
                            .cookie("SESSION", sessionCookie.get())
                            .exchange()

                            // THEN
                            .expectStatus().isOk();
                });
    }

    @Test
    void test_createUser_noPermission() {
        // GIVEN
        createUserIfNotExists("user", "password", List.of());
        AtomicReference<String> sessionCookie = new AtomicReference<>();
        client
                .post()
                .uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new SessionRestController.LoginRequest("user", "password")))
                .exchange()
                .expectStatus().isOk()
                .expectCookie().value("SESSION", sessionCookie::set);

        // WHEN
        client
                .post()
                .uri("/user")
                .cookie("SESSION", sessionCookie.get())
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new SessionRestController.CreateUserRequest("otheruser", "password")))
                .exchange()

                // THEN
                .expectStatus().isForbidden();
    }

    @Test
    void test_createUser() {
        // GIVEN
        createUserIfNotExists("admin", "password", List.of("ROLE_ADMIN"));
        AtomicReference<String> sessionCookie = new AtomicReference<>();
        client
                .post()
                .uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new SessionRestController.LoginRequest("admin", "password")))
                .exchange()
                .expectStatus().isOk()
                .expectCookie().value("SESSION", sessionCookie::set);

        // WHEN
        client
                .post()
                .uri("/user")
                .cookie("SESSION", sessionCookie.get())
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new SessionRestController.CreateUserRequest("otheruser", "password")))
                .exchange()

                // THEN
                .expectStatus().isCreated();
    }

    public void createUserIfNotExists(String username, String password, List<String> roles) {
        if (userRepository.countByUsername(username) == 0) {
            userRepository.save(User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .roles(roles)
                    .build());
        }
    }
}
