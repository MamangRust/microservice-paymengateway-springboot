package com.topupservice.topupservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.topupservice.topupservice.dto.TopupMapper;
import com.topupservice.topupservice.dto.TopupMapperImpl;
import com.topupservice.topupservice.dto.TopupRequest;
import com.topupservice.topupservice.entity.Status;
import com.topupservice.topupservice.entity.Topup;
import com.topupservice.topupservice.exc.GeneralExceptionHandler;
import com.topupservice.topupservice.service.TopupService;

@ExtendWith(MockitoExtension.class)
class TopupControllerTest {

    @Mock
    private TopupService topupService;

    private MockMvc mockMvc;

    private final TopupMapper topupMapper = new TopupMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        TopupController controller = new TopupController(topupService, topupMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Topup createTopup(Long topupId, String cardNumber, Integer amount) {
        Topup topup = new Topup();
        topup.setTopupId(topupId);
        topup.setTopupNo("topup-no-" + topupId);
        topup.setCardNumber(cardNumber);
        topup.setTopupAmount(amount);
        topup.setTopupMethod("BANK_TRANSFER");
        topup.setStatus(Status.PENDING);
        topup.setCreatedAt(LocalDateTime.of(2026, 9, 4, 10, 0));
        return topup;
    }

    // ---- GET /topups ----

    @Test
    void getAllTopups_returnsMappedList() throws Exception {
        when(topupService.getAll()).thenReturn(List.of(createTopup(1L, "C-001", 50000)));

        mockMvc.perform(get("/topups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topupId").value(1))
                .andExpect(jsonPath("$[0].cardNumber").value("C-001"))
                .andExpect(jsonPath("$[0].topupAmount").value(50000))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getAllTopups_returnsEmptyListWhenNone() throws Exception {
        when(topupService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/topups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- GET /topups/{id} (in-handler try/catch -> 404 raw message) ----

    @Test
    void getTopupById_returnsResponse() throws Exception {
        when(topupService.getById(1L)).thenReturn(createTopup(1L, "C-001", 50000));

        mockMvc.perform(get("/topups/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topupId").value(1))
                .andExpect(jsonPath("$.topupNo").value("topup-no-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getTopupById_returns404WithRawMessageWhenNotFound() throws Exception {
        when(topupService.getById(99L)).thenThrow(new RuntimeException("Topup not found"));

        // in-handler catch: body is the raw message string, not ErrorResponse
        mockMvc.perform(get("/topups/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Topup not found"));
    }

    // ---- POST /topups (in-handler try/catch -> 409 on any RuntimeException) ----

    @Test
    void createTopup_returnsCreatedTopup() throws Exception {
        TopupRequest request = new TopupRequest("C-001", 50000, "BANK_TRANSFER", "idem-new");

        when(topupService.create(any(TopupRequest.class))).thenReturn(createTopup(7L, "C-001", 50000));

        mockMvc.perform(post("/topups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topupId").value(7))
                .andExpect(jsonPath("$.cardNumber").value("C-001"))
                .andExpect(jsonPath("$.topupAmount").value(50000))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createTopup_returns409WithRawMessageWhenDuplicateIdempotencyKey() throws Exception {
        TopupRequest request = new TopupRequest("C-001", 50000, "BANK_TRANSFER", "idem-dup");

        when(topupService.create(any(TopupRequest.class)))
                .thenThrow(new RuntimeException("Duplicate idempotency key"));

        // POST catch block maps RuntimeException to CONFLICT with the raw message
        mockMvc.perform(post("/topups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$").value("Duplicate idempotency key"));
    }

    @Test
    void createTopup_returns409WithRawMessageWhenServiceFailsForOtherReason() throws Exception {
        when(topupService.create(any(TopupRequest.class)))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post("/topups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TopupRequest("C-001", 50000, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$").value("db down"));
    }

    @Test
    void createTopup_returns400WhenCardNumberBlank() throws Exception {
        TopupRequest request = new TopupRequest(" ", 50000, "QRIS", null);

        mockMvc.perform(post("/topups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(topupService, never()).create(any(TopupRequest.class));
    }

    @Test
    void createTopup_returns400WhenAmountMissing() throws Exception {
        String body = "{\"cardNumber\": \"C-001\", \"topupAmount\": null}";

        mockMvc.perform(post("/topups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(topupService, never()).create(any(TopupRequest.class));
    }

    // ---- PUT /topups/{id} (in-handler try/catch -> 404) ----

    @Test
    void updateTopup_returnsUpdatedResponse() throws Exception {
        TopupRequest request = new TopupRequest("C-200", 250000, "QRIS", null);

        when(topupService.update(eq(1L), any(TopupRequest.class)))
                .thenReturn(createTopup(1L, "C-200", 250000));

        mockMvc.perform(put("/topups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumber").value("C-200"))
                .andExpect(jsonPath("$.topupAmount").value(250000))
                .andExpect(jsonPath("$.topupMethod").value("BANK_TRANSFER"));
    }

    @Test
    void updateTopup_returns404WithRawMessageWhenNotFound() throws Exception {
        TopupRequest request = new TopupRequest("C-200", 100, null, null);

        when(topupService.update(eq(99L), any(TopupRequest.class)))
                .thenThrow(new RuntimeException("Topup not found"));

        mockMvc.perform(put("/topups/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Topup not found"));
    }

    // ---- DELETE /topups/{id} (in-handler try/catch) ----

    @Test
    void deleteTopup_returnsSuccessMessage() throws Exception {
        mockMvc.perform(delete("/topups/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Topup deleted"));

        verify(topupService).delete(1L);
    }

    @Test
    void deleteTopup_returns404WithRawMessageWhenServiceFails() throws Exception {
        doThrow(new RuntimeException("Topup not found")).when(topupService).delete(99L);

        mockMvc.perform(delete("/topups/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Topup not found"));
    }

    // ---- uncaught paths go through the @RestControllerAdvice ----

    @Test
    void getAllTopups_returns500WithErrorResponseWhenServiceFails() throws Exception {
        // GET has no in-handler try/catch — the exception reaches the advice,
        // which wraps it into the compact ErrorResponse record
        when(topupService.getAll()).thenThrow(new RuntimeException("db down"));

        mockMvc.perform(get("/topups"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }
}
