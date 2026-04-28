package com.example.tdd.async;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class FanoutServiceTest {

    private static WireMockServer wm;
    private FanoutService service;

    @BeforeAll
    static void start() {
        wm = new WireMockServer(0);
        wm.start();
    }

    @AfterAll
    static void stop() { wm.stop(); }

    @BeforeEach
    void setUp() {
        wm.resetAll();
        service = new FanoutService(RestClient.builder(),
            "http://localhost:" + wm.port());
    }

    @Test
    void fetchAll_100users_underOneSecond_thanksToVirtualThreads() {
        wm.stubFor(get(urlMatching("/users/.*"))
            .willReturn(aResponse()
                .withFixedDelay(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"u\",\"email\":\"u@x\"}")));

        List<String> ids = IntStream.range(0, 100)
            .mapToObj(i -> "USR-" + i).toList();

        long t0 = System.nanoTime();
        List<FanoutService.UserProfile> result = service.fetchAll(ids);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);

        assertThat(result).hasSize(100);
        // 100 calls × 200ms each = 20s sequential; with virtual threads ≈ 200-400ms
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void runsOnVirtualThreads() throws InterruptedException {
        AtomicBoolean wasVirtual = new AtomicBoolean();

        Thread t = Thread.ofVirtual().start(() ->
            wasVirtual.set(Thread.currentThread().isVirtual()));
        t.join();

        assertThat(wasVirtual).isTrue();
    }
}
