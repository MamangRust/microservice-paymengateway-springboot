package com.transactionservice.transactionservice.controller;

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
import com.transactionservice.transactionservice.dto.TransactionMapper;
import com.transactionservice.transactionservice.dto.TransactionMapperImpl;
import com.transactionservice.transactionservice.dto.TransactionRequest;
import com.transactionservice.transactionservice.entity.Status;
import com.transactionservice.transactionservice.entity.Transaction;
import com.transactionservice.transactionservice.exc.GeneralExceptionHandler;
import com.transactionservice.transactionservice.service.TransactionService;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;

    private MockMvc mockMvc;

    private final TransactionMapper transactionMapper = new TransactionMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        TransactionController controller = new TransactionController(transactionService, transactionMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Transaction transaction(Long id, String cardNumber, Integer amount, Status status) {
        Transaction txn = new Transaction();
        txn.setTransactionId(id);
        txn.setCardNumber(cardNumber);
        txn.setAmount(amount);
        txn.setPaymentMethod("QRIS");
        txn.setMerchantId(1);
        txn.setStatus(status);
        return txn;
    }

    // ---- GET /transactions ----

    @Test
    void getAllTransactions_returnsMappedList() throws Exception {
        when(transactionService.getAll())
                .thenReturn(List.of(transaction(1L, "4111111111111111", 111000, Status.PENDING)));

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value(1))
                .andExpect(jsonPath("$[0].cardNumber").value("4111111111111111"))
                .andExpect(jsonPath("$[0].amount").value(111000))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getAllTransactions_returnsEmptyListWhenNone() throws Exception {
        when(transactionService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- GET /transactions/{id} ----

    @Test
    void getTransactionById_returnsResponse() throws Exception {
        when(transactionService.getById(1L)).thenReturn(transaction(1L, "4111111111111111", 111000, Status.PENDING));

        mockMvc.perform(get("/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(1))
                .andExpect(jsonPath("$.paymentMethod").value("QRIS"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getTransactionById_returns404WhenNotFound() throws Exception {
        // quirk: the controller's inline catch maps ANY RuntimeException to 404
        when(transactionService.getById(99L)).thenThrow(new RuntimeException("Transaction not found"));

        mockMvc.perform(get("/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Transaction not found"));
    }

    // ---- POST /transactions ----

    @Test
    void createTransaction_returnsMappedResponse() throws Exception {
        String body = """
                {"cardNumber": "4111111111111111", "amount": 111000, "paymentMethod": "QRIS", "merchantId": 1, "idempotencyKey": "idem-1"}
                """;

        when(transactionService.create(any(TransactionRequest.class)))
                .thenReturn(transaction(5L, "4111111111111111", 111000, Status.PENDING));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(5))
                .andExpect(jsonPath("$.amount").value(111000));

        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).create(captor.capture());
        assertThat(captor.getValue().cardNumber()).isEqualTo("4111111111111111");
        assertThat(captor.getValue().amount()).isEqualTo(111000);
        assertThat(captor.getValue().paymentMethod()).isEqualTo("QRIS");
        assertThat(captor.getValue().merchantId()).isEqualTo(1);
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("idem-1");
    }

    @Test
    void createTransaction_returns409WhenDuplicate() throws Exception {
        String body = """
                {"cardNumber": "4111111111111111", "amount": 111000, "idempotencyKey": "idem-1"}
                """;

        when(transactionService.create(any(TransactionRequest.class)))
                .thenThrow(new RuntimeException("Duplicate idempotency key"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$").value("Duplicate idempotency key"));
    }

    @Test
    void createTransaction_mapsAnyServiceFailureTo409() throws Exception {
        // quirk: the POST catch block is a catch-all RuntimeException -> 409,
        // so even "db down" style failures surface as Conflict
        String body = """
                {"cardNumber": "4111111111111111", "amount": 111000}
                """;

        when(transactionService.create(any(TransactionRequest.class)))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$").value("db down"));
    }

    // ---- POST validation (via GeneralExceptionHandler) ----

    @Test
    void createTransaction_returns400WhenCardNumberMissing() throws Exception {
        String body = """
                {"amount": 111000}
                """;

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(transactionService, never()).create(any());
    }

    @Test
    void createTransaction_returns400WhenAmountMissing() throws Exception {
        String body = """
                {"cardNumber": "4111111111111111"}
                """;

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(transactionService, never()).create(any());
    }

    // ---- PUT /transactions/{id} ----

    @Test
    void updateTransaction_returnsMappedResponse() throws Exception {
        String body = """
                {"cardNumber": "4222222222222222", "amount": 99000, "paymentMethod": "CASH", "merchantId": 2}
                """;

        when(transactionService.update(org.mockito.ArgumentMatchers.eq(1L), any(TransactionRequest.class)))
                .thenReturn(transaction(1L, "4222222222222222", 99000, Status.PENDING));

        mockMvc.perform(put("/transactions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(1))
                .andExpect(jsonPath("$.cardNumber").value("4222222222222222"))
                .andExpect(jsonPath("$.amount").value(99000));
    }

    @Test
    void updateTransaction_returns404WhenNotFound() throws Exception {
        String body = """
                {"cardNumber": "4222222222222222", "amount": 99000}
                """;

        when(transactionService.update(org.mockito.ArgumentMatchers.eq(99L), any(TransactionRequest.class)))
                .thenThrow(new RuntimeException("Transaction not found"));

        mockMvc.perform(put("/transactions/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Transaction not found"));
    }

    // ---- DELETE /transactions/{id} ----

    @Test
    void deleteTransaction_returnsMessage() throws Exception {
        mockMvc.perform(delete("/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Transaction deleted"));

        verify(transactionService).delete(1L);
    }

    @Test
    void deleteTransaction_returns404WhenServiceFails() throws Exception {
        doThrow(new RuntimeException("Transaction not found")).when(transactionService).delete(99L);

        mockMvc.perform(delete("/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Transaction not found"));
    }

    // ---- serialization shape ----

    @Test
    void responseContainsTimestamps() throws Exception {
        Transaction txn = transaction(1L, "4111111111111111", 111000, Status.PENDING);
        txn.setTransactionTime(java.time.LocalDateTime.of(2026, 9, 4, 10, 0));

        when(transactionService.getById(1L)).thenReturn(txn);

        mockMvc.perform(get("/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionTime[0]").value(2026));
    }
}
