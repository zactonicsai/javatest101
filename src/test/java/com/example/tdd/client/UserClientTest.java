package com.example.tdd.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserClientTest {

    private static WireMockServer wm;
    private UserClient client;

    @BeforeAll
    static void start() { wm = new WireMockServer(0); wm.start(); }
    @AfterAll
    static void stop()  { wm.stop(); }

    @BeforeEach
    void reset() {
        wm.resetAll();
        client = new UserClient(RestClient.builder(),
            "http://localhost:" + wm.port());
    }

    @Test
    void existingUser_returnsPresentOptional() {
        wm.stubFor(get(urlEqualTo("/users/USR-1"))
            .willReturn(okJson("{\"id\":\"USR-1\",\"email\":\"alice@example.com\"}")));

        Optional<UserClient.User> u = client.findById("USR-1");

        assertThat(u).isPresent();
        assertThat(u.get().email()).isEqualTo("alice@example.com");
    }

    @Test
    void notFound_returnsEmptyOptional() {
        wm.stubFor(get(urlEqualTo("/users/USR-404"))
            .willReturn(aResponse().withStatus(404)));

        Optional<UserClient.User> u = client.findById("USR-404");

        assertThat(u).isEmpty();
    }

    @Test
    void serverError_throwsServiceUnavailable() {
        wm.stubFor(get(urlPathMatching("/users/.+"))
            .willReturn(aResponse().withStatus(503)
                .withBody("{\"error\":\"upstream_unavailable\"}")));

        assertThatThrownBy(() -> client.findById("USR-1"))
            .isInstanceOf(UserClient.ServiceUnavailableException.class)
            .hasMessageContaining("503");
    }

    @Test
    void malformedJson_throwsDeserializationError() {
        wm.stubFor(get(urlPathMatching("/users/.+"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{ not valid json")));

        assertThatThrownBy(() -> client.findById("USR-1"))
            .hasRootCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
}
