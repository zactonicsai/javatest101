package com.example.tdd.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ResilientPaymentClientTest.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class ResilientPaymentClientTest {

    @Configuration
    @EnableRetry
    static class TestConfig {
        @Bean public RestClient.Builder restClientBuilder() { return RestClient.builder(); }
        @Bean public ResilientPaymentClient client(RestClient.Builder b,
                @org.springframework.beans.factory.annotation.Value("${payment-service.base-url}") String url) {
            return new ResilientPaymentClient(b, url);
        }
    }

    private static WireMockServer wm;

    @BeforeAll
    static void start() {
        wm = new WireMockServer(0);
        wm.start();
    }
    @AfterAll
    static void stop() { wm.stop(); }

    @DynamicPropertySource
    static void wmProps(DynamicPropertyRegistry r) {
        r.add("payment-service.base-url", () -> "http://localhost:" + wm.port());
    }

    @BeforeEach
    void reset() { wm.resetAll(); }

    @Autowired ResilientPaymentClient client;

    @Test
    void retries_thenSucceeds_onTransientFailure() {
        wm.stubFor(post(urlEqualTo("/charge"))
            .inScenario("retry").whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER))
            .willSetStateTo("one"));
        wm.stubFor(post(urlEqualTo("/charge"))
            .inScenario("retry").whenScenarioStateIs("one")
            .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER))
            .willSetStateTo("two"));
        wm.stubFor(post(urlEqualTo("/charge"))
            .inScenario("retry").whenScenarioStateIs("two")
            .willReturn(okJson("{\"id\":\"REQ-1\",\"status\":\"OK\",\"authCode\":\"AUTH-1\"}")));

        var result = client.charge(new ResilientPaymentClient.ChargeRequest("REQ-1", BigDecimal.TEN));

        assertThat(result.status()).isEqualTo("OK");
        wm.verify(3, postRequestedFor(urlEqualTo("/charge")));
    }

    @Test
    void exhaustsAttempts_thenFallsBackToDeferred() {
        wm.stubFor(post(urlEqualTo("/charge"))
            .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        var result = client.charge(new ResilientPaymentClient.ChargeRequest("REQ-2", BigDecimal.TEN));

        assertThat(result.status()).isEqualTo("DEFERRED");
        // 4 attempts (max 4 per @Retryable config)
        wm.verify(4, postRequestedFor(urlEqualTo("/charge")));
    }
}
