package com.saldoservice.saldoservice.controller;

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
import com.saldoservice.saldoservice.dto.SaldoMapper;
import com.saldoservice.saldoservice.dto.SaldoMapperImpl;
import com.saldoservice.saldoservice.dto.SaldoRequest;
import com.saldoservice.saldoservice.entity.Saldo;
import com.saldoservice.saldoservice.exc.GeneralExceptionHandler;
import com.saldoservice.saldoservice.service.SaldoService;

@ExtendWith(MockitoExtension.class)
class SaldoControllerTest {

    @Mock
    private SaldoService saldoService;

    private MockMvc mockMvc;

    private final SaldoMapper saldoMapper = new SaldoMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        SaldoController controller = new SaldoController(saldoService, saldoMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Saldo createSaldo(Long saldoId, String cardNumber, Integer totalBalance) {
        Saldo saldo = new Saldo();
        saldo.setSaldoId(saldoId);
        saldo.setCardNumber(cardNumber);
        saldo.setTotalBalance(totalBalance);
        saldo.setCreatedAt(LocalDateTime.of(2026, 9, 4, 10, 0));
        return saldo;
    }

    // ---- GET /saldos ----

    @Test
    void getAllSaldos_returnsMappedList() throws Exception {
        when(saldoService.getAll()).thenReturn(List.of(createSaldo(1L, "C-001", 100)));

        mockMvc.perform(get("/saldos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].saldoId").value(1))
                .andExpect(jsonPath("$[0].cardNumber").value("C-001"))
                .andExpect(jsonPath("$[0].totalBalance").value(100));
    }

    @Test
    void getAllSaldos_returnsEmptyListWhenNone() throws Exception {
        when(saldoService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/saldos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- GET /saldos/{id} (in-handler try/catch -> 404 raw message) ----

    @Test
    void getSaldoById_returnsResponse() throws Exception {
        when(saldoService.getById(1L)).thenReturn(createSaldo(1L, "C-001", 100));

        mockMvc.perform(get("/saldos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoId").value(1))
                .andExpect(jsonPath("$.cardNumber").value("C-001"))
                .andExpect(jsonPath("$.totalBalance").value(100));
    }

    @Test
    void getSaldoById_returns404WithRawMessageWhenNotFound() throws Exception {
        when(saldoService.getById(99L)).thenThrow(new RuntimeException("Saldo not found"));

        // in-handler catch: body is the raw message string, not ErrorResponse
        mockMvc.perform(get("/saldos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Saldo not found"));
    }

    // ---- GET /saldos/card/{cardNumber} (in-handler try/catch -> 404) ----

    @Test
    void getSaldoByCard_returnsResponse() throws Exception {
        when(saldoService.getByCardNumber("C-001")).thenReturn(createSaldo(1L, "C-001", 100));

        mockMvc.perform(get("/saldos/card/C-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumber").value("C-001"))
                .andExpect(jsonPath("$.totalBalance").value(100));
    }

    @Test
    void getSaldoByCard_returns404WithRawMessageWhenNotFound() throws Exception {
        when(saldoService.getByCardNumber("nope")).thenThrow(new RuntimeException("Saldo not found"));

        mockMvc.perform(get("/saldos/card/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Saldo not found"));
    }

    // ---- POST /saldos ----

    @Test
    void createSaldo_returnsCreatedSaldo() throws Exception {
        SaldoRequest request = new SaldoRequest("C-100", 250);

        when(saldoService.create(any(SaldoRequest.class))).thenReturn(createSaldo(7L, "C-100", 250));

        mockMvc.perform(post("/saldos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoId").value(7))
                .andExpect(jsonPath("$.cardNumber").value("C-100"))
                .andExpect(jsonPath("$.totalBalance").value(250));
    }

    @Test
    void createSaldo_returns400WhenCardNumberBlank() throws Exception {
        SaldoRequest request = new SaldoRequest(" ", 100);

        mockMvc.perform(post("/saldos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(saldoService, never()).create(any(SaldoRequest.class));
    }

    @Test
    void createSaldo_returns400WhenTotalBalanceMissing() throws Exception {
        String body = "{\"cardNumber\": \"C-100\", \"totalBalance\": null}";

        mockMvc.perform(post("/saldos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(saldoService, never()).create(any(SaldoRequest.class));
    }

    @Test
    void createSaldo_returns500WithErrorResponseWhenServiceFails() throws Exception {
        // POST has no in-handler try/catch — the exception reaches the advice,
        // which wraps it into the compact ErrorResponse record
        when(saldoService.create(any(SaldoRequest.class)))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post("/saldos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaldoRequest("C-100", 250))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }

    // ---- PUT /saldos/{id} (in-handler try/catch -> 404) ----

    @Test
    void updateSaldo_returnsUpdatedResponse() throws Exception {
        SaldoRequest request = new SaldoRequest("C-200", 500);

        when(saldoService.update(eq(1L), any(SaldoRequest.class)))
                .thenReturn(createSaldo(1L, "C-200", 500));

        mockMvc.perform(put("/saldos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumber").value("C-200"))
                .andExpect(jsonPath("$.totalBalance").value(500));
    }

    @Test
    void updateSaldo_returns404WithRawMessageWhenNotFound() throws Exception {
        SaldoRequest request = new SaldoRequest("C-200", 500);

        when(saldoService.update(eq(99L), any(SaldoRequest.class)))
                .thenThrow(new RuntimeException("Saldo not found"));

        mockMvc.perform(put("/saldos/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Saldo not found"));
    }

    // ---- DELETE /saldos/{id} (in-handler try/catch) ----

    @Test
    void deleteSaldo_returnsSuccessMessage() throws Exception {
        mockMvc.perform(delete("/saldos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Saldo deleted"));

        verify(saldoService).delete(1L);
    }

    @Test
    void deleteSaldo_returns404WithRawMessageWhenServiceFails() throws Exception {
        doThrow(new RuntimeException("Saldo not found")).when(saldoService).delete(99L);

        mockMvc.perform(delete("/saldos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Saldo not found"));
    }
}
