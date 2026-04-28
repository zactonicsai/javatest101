package com.example.tdd.api;

import com.example.tdd.config.SecurityConfig;
import com.example.tdd.domain.exception.NotFoundException;
import com.example.tdd.domain.order.Order;
import com.example.tdd.domain.order.OrderService;
import com.example.tdd.support.OrderFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class OrderControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean OrderService orderService;

    @Test
    void anonymousRequest_returns401() throws Exception {
        mvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_validBody_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        Order persisted = OrderFixtures.simple("USR-1");
        when(orderService.create(any(), any())).thenReturn(persisted);

        var body = """
            {
              "userId":"USR-1",
              "items":[{"sku":"SKU-1","quantity":2,"unitPrice":9.99}],
              "contactEmail":"a@b.com",
              "orderDate":"2026-04-28"
            }
            """;

        mvc.perform(post("/api/v1/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value("USR-1"))
            .andExpect(jsonPath("$.lines", hasSize(2)));
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_emptyBody_returnsAllFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[*].field",
                containsInAnyOrder("userId", "items")));

        verifyNoInteractions(orderService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_negativeQuantity_returnsTargetedError() throws Exception {
        var body = """
            {"userId":"USR-1","items":[{"sku":"SKU-1","quantity":-3,"unitPrice":1.00}],
             "contactEmail":"a@b.com","orderDate":"2026-04-28"}
            """;
        mvc.perform(post("/api/v1/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_malformedJson_returns400_withMalformedCode() throws Exception {
        mvc.perform(post("/api/v1/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_wrongContentType_returns415() throws Exception {
        mvc.perform(post("/api/v1/orders")
                .with(csrf())
                .contentType(MediaType.TEXT_PLAIN)
                .content("anything"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getById_existing_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.findById(id)).thenReturn(Optional.of(OrderFixtures.simple("USR-1")));

        mvc.perform(get("/api/v1/orders/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("USR-1"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getById_missing_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.findById(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/orders/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void cancel_userRole_returns403() throws Exception {
        mvc.perform(delete("/api/v1/orders/{id}", UUID.randomUUID()).with(csrf()))
            .andExpect(status().isForbidden());

        verifyNoInteractions(orderService);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void cancel_managerRole_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(delete("/api/v1/orders/{id}", id).with(csrf()))
            .andExpect(status().isNoContent());

        verify(orderService).cancel(id);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void cancel_missingOrder_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new NotFoundException("Order " + id)).when(orderService).cancel(id);

        mvc.perform(delete("/api/v1/orders/{id}", id).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancel_adminRole_returns204() throws Exception {
        mvc.perform(delete("/api/v1/orders/{id}", UUID.randomUUID()).with(csrf()))
            .andExpect(status().isNoContent());
    }
}
