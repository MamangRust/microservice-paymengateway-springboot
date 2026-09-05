package com.withdrawservice.withdrawservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.withdrawservice.withdrawservice.dto.WithdrawMapper;
import com.withdrawservice.withdrawservice.dto.WithdrawMapperImpl;
import com.withdrawservice.withdrawservice.dto.WithdrawRequest;
import com.withdrawservice.withdrawservice.entity.Status;
import com.withdrawservice.withdrawservice.entity.Withdraw;
import com.withdrawservice.withdrawservice.exc.GeneralExceptionHandler;
import com.withdrawservice.withdrawservice.service.WithdrawService;

@ExtendWith(MockitoExtension.class)
class WithdrawControllerTest {

    @Mock
    private WithdrawService withdrawService;

    private MockMvc mockMvc;

    private final WithdrawMapper withdrawMapper = new WithdrawMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        WithdrawController controller = new WithdrawController(withdrawService, withdrawMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Withdraw withdraw(Long id, String cardNumber, Integer amount, Status status) {
        Withdraw w = new Withdraw();
        w.setWithdrawId(id);
        w.setCardNumber(cardNumber);
        w.setWithdrawAmount(amount);
        w.setStatus(status);
        return w;
    }

    // ---- GET /withdraws ----

    @Test
    void getAllWithdraws_returnsMappedList() throws Exception {
        when(withdrawService.getAll())
                .thenReturn(List.of(withdraw(1L, "4111111111111111", 250000, Status.PENDING)));

        mockMvc.perform(get("/withdraws"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].withdrawId").value(1))
                .andExpect(jsonPath("$[0].cardNumber").value("4111111111111111"))
                .andExpect(jsonPath("$[0].withdrawAmount").value(250000))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getAllWithdraws_returnsEmptyListWhenNone() throws Exception {
        when(withdrawService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/withdraws"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- GET /withdraws/{id} ----

    @Test
    void getWithdrawById_returnsResponse() throws Exception {
        when(withdrawService.getById(1L)).thenReturn(withdraw(1L, "4111111111111111", 250000, Status.PENDING));

        mockMvc.perform(get("/withdraws/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawId").value(1))
                .andExpect(jsonPath("$.withdrawAmount").value(250000));
    }

    @Test
    void getWithdrawById_returns404WhenNotFound() throws Exception {
        // quirk: the controller's inline catch maps ANY RuntimeException to 404
        when(withdrawService.getById(99L)).thenThrow(new RuntimeException("Withdraw not found"));

        mockMvc.perform(get("/withdraws/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Withdraw not found"));
    }

    // ---- POST /withdraws ----

    @Test
    void createWithdraw_returnsMappedResponse() throws Exception {
        String body = """
                {"cardNumber": "4111111111111111", "withdrawAmount": 250000, "idempotencyKey": "idem-1"}
                """;

        when(withdrawService.create(any(WithdrawRequest.class)))
                .thenReturn(withdraw(5L, "4111111111111111", 250000, Status.PENDING));

        mockMvc.perform(post("/withdraws")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawId").value(5))
                .andExpect(jsonPath("$.withdrawAmount").value(250000));

        ArgumentCaptor<WithdrawRequest> captor = ArgumentCaptor.forClass(WithdrawRequest.class);
        verify(withdrawService).create(captor.capture());
        assertThat(captor.getValue().cardNumber()).isEqualTo("4111111111111111");
        assertThat(captor.getValue().withdrawAmount()).isEqualTo(250000);
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("idem-1");
    }

    @Test
    void createWithdraw_returns409WhenDuplicate() throws Exception {
        String body = """
                {"cardNumber": "4111111111111111", "withdrawAmount": 250000, "idempotencyKey": "idem-1"}
                """;

        when(withdrawService.create(any(WithdrawRequest.class)))
                .thenThrow(new RuntimeException("Duplicate idempotency key"));

        mockMvc.perform(post("/withdraws")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$").value("Duplicate idempotency key"));
    }

    // ---- POST validation (via GeneralExceptionHandler) ----

    @Test
    void createWithdraw_returns400WhenCardNumberMissing() throws Exception {
        String body = """
                {"withdrawAmount": 250000}
                """;

        mockMvc.perform(post("/withdraws")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(withdrawService, never()).create(any());
    }

    @Test
    void createWithdraw_returns400WhenAmountMissing() throws Exception {
        String body = """
                {"cardNumber": "4111111111111111"}
                """;

        mockMvc.perform(post("/withdraws")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(withdrawService, never()).create(any());
    }

    // ---- PUT /withdraws/{id} ----

    @Test
    void updateWithdraw_returnsMappedResponse() throws Exception {
        String body = """
                {"cardNumber": "4222222222222222", "withdrawAmount": 99000}
                """;

        when(withdrawService.update(org.mockito.ArgumentMatchers.eq(1L), any(WithdrawRequest.class)))
                .thenReturn(withdraw(1L, "4222222222222222", 99000, Status.PENDING));

        mockMvc.perform(put("/withdraws/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawId").value(1))
                .andExpect(jsonPath("$.cardNumber").value("4222222222222222"))
                .andExpect(jsonPath("$.withdrawAmount").value(99000));
    }

    @Test
    void updateWithdraw_returns404WhenNotFound() throws Exception {
        String body = """
                {"cardNumber": "4222222222222222", "withdrawAmount": 99000}
                """;

        when(withdrawService.update(org.mockito.ArgumentMatchers.eq(99L), any(WithdrawRequest.class)))
                .thenThrow(new RuntimeException("Withdraw not found"));

        mockMvc.perform(put("/withdraws/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Withdraw not found"));
    }

    // ---- DELETE /withdraws/{id} ----

    @Test
    void deleteWithdraw_returnsMessage() throws Exception {
        mockMvc.perform(delete("/withdraws/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Withdraw deleted"));

        verify(withdrawService).delete(1L);
    }

    @Test
    void deleteWithdraw_returns404WhenServiceFails() throws Exception {
        doThrow(new RuntimeException("Withdraw not found")).when(withdrawService).delete(99L);

        mockMvc.perform(delete("/withdraws/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Withdraw not found"));
    }

    // ---- serialization shape ----

    @Test
    void responseContainsTimestamps() throws Exception {
        Withdraw w = withdraw(1L, "4111111111111111", 250000, Status.PENDING);
        w.setWithdrawTime(java.time.LocalDateTime.of(2026, 9, 4, 10, 0));

        when(withdrawService.getById(1L)).thenReturn(w);

        mockMvc.perform(get("/withdraws/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawTime[0]").value(2026));
    }
}
