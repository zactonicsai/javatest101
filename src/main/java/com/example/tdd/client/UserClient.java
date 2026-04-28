package com.example.tdd.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Service
public class UserClient {

    private final RestClient http;

    public UserClient(RestClient.Builder builder,
                      @Value("${user-service.base-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public Optional<User> findById(String id) {
        try {
            User u = http.get().uri("/users/{id}", id)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    if (res.getStatusCode().value() == 404) return; // swallow → empty
                    throw new ServiceException("Client error: " + res.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new ServiceUnavailableException("Upstream " + res.getStatusCode());
                })
                .body(User.class);
            return Optional.ofNullable(u);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public record User(String id, String email) {}

    public static class ServiceException extends RuntimeException {
        public ServiceException(String message) { super(message); }
    }
    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) { super(message); }
    }
}
