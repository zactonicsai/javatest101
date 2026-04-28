package com.example.tdd.api;

import com.example.tdd.api.dto.CreateOrderRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestValidationTest {

    private final Validator validator = Validation
        .buildDefaultValidatorFactory().getValidator();

    @Test
    void validRequest_hasNoViolations() {
        CreateOrderRequest req = sample();

        Set<ConstraintViolation<CreateOrderRequest>> v = validator.validate(req);

        assertThat(v).isEmpty();
    }

    @ParameterizedTest(name = "blank userId [\"{0}\"] is rejected")
    @ValueSource(strings = {"", " ", "\t"})
    void blankUserId_isRejected(String userId) {
        var req = new CreateOrderRequest(
            userId, sample().items(),
            sample().contactEmail(), sample().orderDate(), null);

        assertThat(validator.validate(req))
            .extracting(ConstraintViolation::getMessage)
            .contains("userId is required");
    }

    @Test
    void emptyItems_isRejected() {
        var req = new CreateOrderRequest(
            "USR-1", List.of(), "a@b.com", LocalDate.now(), null);

        assertThat(validator.validate(req))
            .extracting(v -> v.getPropertyPath().toString())
            .contains("items");
    }

    @Test
    void zeroQuantity_isRejected() {
        var bad = new CreateOrderRequest.OrderLine("SKU-1", 0, BigDecimal.ONE);
        var req = new CreateOrderRequest(
            "USR-1", List.of(bad), "a@b.com", LocalDate.now(), null);

        assertThat(validator.validate(req))
            .extracting(v -> v.getPropertyPath().toString())
            .contains("items[0].quantity");
    }

    @Test
    void negativePrice_isRejected() {
        var bad = new CreateOrderRequest.OrderLine("SKU-1", 1, new BigDecimal("-0.01"));
        var req = new CreateOrderRequest(
            "USR-1", List.of(bad), "a@b.com", LocalDate.now(), null);

        assertThat(validator.validate(req))
            .extracting(v -> v.getPropertyPath().toString())
            .contains("items[0].unitPrice");
    }

    @Test
    void invalidEmail_isRejected() {
        var req = new CreateOrderRequest(
            "USR-1", sample().items(),
            "not-an-email", LocalDate.now(), null);

        assertThat(validator.validate(req))
            .extracting(v -> v.getPropertyPath().toString())
            .contains("contactEmail");
    }

    @Test
    void futureOrderDate_isRejected() {
        var req = new CreateOrderRequest(
            "USR-1", sample().items(),
            "a@b.com", LocalDate.now().plusDays(1), null);

        assertThat(validator.validate(req))
            .extracting(v -> v.getPropertyPath().toString())
            .contains("orderDate");
    }

    @Test
    void quantityOverHundred_isRejected() {
        var bad = new CreateOrderRequest.OrderLine("SKU-1", 101, BigDecimal.ONE);
        var req = new CreateOrderRequest(
            "USR-1", List.of(bad), "a@b.com", LocalDate.now(), null);

        assertThat(validator.validate(req))
            .extracting(ConstraintViolation::getMessage)
            .contains("quantity must be ≤ 100 per line");
    }

    private static CreateOrderRequest sample() {
        return new CreateOrderRequest(
            "USR-1",
            List.of(new CreateOrderRequest.OrderLine("SKU-1", 2, new BigDecimal("9.99"))),
            "alice@example.com",
            LocalDate.now(),
            null
        );
    }
}
