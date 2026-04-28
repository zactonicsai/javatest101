package com.example.tdd.e2e;

import com.example.tdd.api.dto.CreateOrderRequest;
import com.example.tdd.api.dto.OrderResponse;
import com.example.tdd.domain.order.OrderRepository;
import com.example.tdd.support.OrderFixtures;
import com.redis.testcontainers.RedisContainer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Full-stack integration test:
 *
 * <ul>
 *   <li>Real Postgres (Testcontainers) — order persistence</li>
 *   <li>Real Redis (Testcontainers) — cache for findById</li>
 *   <li>Real LocalStack — S3 invoice + SQS event</li>
 *   <li>Mocked JwtDecoder — simulates a Keycloak-issued JWT without spinning Keycloak</li>
 * </ul>
 *
 * <p>For a Keycloak-backed variant, swap the JwtDecoder mock for a real KeycloakContainer
 * and the password-grant token-fetch shown in the tutorial section 18.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class CreateOrderE2ETest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Container @ServiceConnection
    static final RedisContainer redis =
        new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Container
    static final LocalStackContainer localStack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8"))
        .withServices(Service.S3, Service.SQS);

    @DynamicPropertySource
    static void wireAws(DynamicPropertyRegistry r) {
        r.add("aws.endpoint",   () -> localStack.getEndpoint().toString());
        r.add("aws.region",     localStack::getRegion);
        r.add("aws.access-key", localStack::getAccessKey);
        r.add("aws.secret-key", localStack::getSecretKey);
        r.add("aws.s3.bucket-uploads", () -> "app-uploads");
        r.add("aws.sqs.order-events-queue", () -> "order-events");
    }

    @BeforeAll
    static void provision() throws Exception {
        localStack.execInContainer("awslocal", "s3",  "mb",            "s3://app-uploads");
        localStack.execInContainer("awslocal", "sqs", "create-queue",  "--queue-name", "order-events");
    }

    @LocalServerPort int port;

    @Autowired OrderRepository orderRepo;
    @Autowired CacheManager cacheManager;
    @Autowired S3Client s3;
    @Autowired SqsClient sqs;
    @Autowired RestClient.Builder restBuilder;

    /** Replace JWT validation with a stub that issues a USER-role token for "USR-alice". */
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void createOrder_persists_caches_storesInvoice_publishesEvent() {
        when(jwtDecoder.decode(any())).thenReturn(stubJwtFor("USR-alice", "USER"));

        CreateOrderRequest request = OrderFixtures.validRequestFor("USR-alice");

        ResponseEntity<OrderResponse> response = restBuilder.build()
            .post()
            .uri("http://localhost:" + port + "/api/v1/orders")
            .header("Authorization", "Bearer fake-token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID orderId = response.getBody().id();

        // 1) Postgres
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(orderRepo.findById(orderId)).isPresent());

        // 2) S3 invoice landed
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ListObjectsV2Response listing = s3.listObjectsV2(b -> b.bucket("app-uploads"));
            assertThat(listing.contents())
                .extracting(S3Object::key)
                .anyMatch(k -> k.contains(orderId.toString()));
        });

        // 3) SQS message present
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String url = sqs.getQueueUrl(GetQueueUrlRequest.builder()
                .queueName("order-events").build()).queueUrl();
            List<Message> msgs = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(url).waitTimeSeconds(2).maxNumberOfMessages(10).build()).messages();
            assertThat(msgs).extracting(Message::body)
                .anyMatch(s -> s.contains(orderId.toString()));
        });

        // 4) Subsequent GET hits the cache (Redis) — second call should be served from cache.
        // We can't easily prove "no DB call" here without spying, but we can prove the cache
        // entry exists.
        restBuilder.build()
            .get()
            .uri("http://localhost:" + port + "/api/v1/orders/{id}", orderId)
            .header("Authorization", "Bearer fake-token")
            .retrieve()
            .toBodilessEntity();

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(cacheManager.getCache("orders").get(orderId)).isNotNull());
    }

    private static Jwt stubJwtFor(String userId, String role) {
        return Jwt.withTokenValue("fake-token")
            .header("alg", "none")
            .subject(userId)
            .claim("realm_access", Map.of("roles", List.of(role)))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    }
}
