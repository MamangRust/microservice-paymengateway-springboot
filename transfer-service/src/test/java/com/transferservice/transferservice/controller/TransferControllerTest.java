package com.transferservice.transferservice.controller;

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
import com.transferservice.transferservice.dto.TransferMapper;
import com.transferservice.transferservice.dto.TransferMapperImpl;
import com.transferservice.transferservice.dto.TransferRequest;
import com.transferservice.transferservice.entity.Status;
import com.transferservice.transferservice.entity.Transfer;
import com.transferservice.transferservice.exc.GeneralExceptionHandler;
import com.transferservice.transferservice.service.TransferService;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock
    private TransferService transferService;

    private MockMvc mockMvc;

    private final TransferMapper transferMapper = new TransferMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        TransferController controller = new TransferController(transferService, transferMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Transfer transfer(Long id, String from, String to, Integer amount, Status status) {
        Transfer t = new Transfer();
        t.setTransferId(id);
        t.setTransferFrom(from);
        t.setTransferTo(to);
        t.setTransferAmount(amount);
        t.setStatus(status);
        return t;
    }

    // ---- GET /transfers ----

    @Test
    void getAllTransfers_returnsMappedList() throws Exception {
        when(transferService.getAll())
                .thenReturn(List.of(transfer(1L, "ACC-001", "ACC-002", 250000, Status.PENDING)));

        mockMvc.perform(get("/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transferId").value(1))
                .andExpect(jsonPath("$[0].transferFrom").value("ACC-001"))
                .andExpect(jsonPath("$[0].transferTo").value("ACC-002"))
                .andExpect(jsonPath("$[0].transferAmount").value(250000))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getAllTransfers_returnsEmptyListWhenNone() throws Exception {
        when(transferService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- GET /transfers/{id} ----

    @Test
    void getTransferById_returnsResponse() throws Exception {
        when(transferService.getById(1L)).thenReturn(transfer(1L, "ACC-001", "ACC-002", 250000, Status.PENDING));

        mockMvc.perform(get("/transfers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(1))
                .andExpect(jsonPath("$.transferAmount").value(250000));
    }

    @Test
    void getTransferById_returns404WhenNotFound() throws Exception {
        // quirk: the controller's inline catch maps ANY RuntimeException to 404
        when(transferService.getById(99L)).thenThrow(new RuntimeException("Transfer not found"));

        mockMvc.perform(get("/transfers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Transfer not found"));
    }

    // ---- POST /transfers ----

    @Test
    void createTransfer_returnsMappedResponse() throws Exception {
        String body = """
                {"transferFrom": "ACC-001", "transferTo": "ACC-002", "transferAmount": 250000, "idempotencyKey": "idem-1"}
                """;

        when(transferService.create(any(TransferRequest.class)))
                .thenReturn(transfer(5L, "ACC-001", "ACC-002", 250000, Status.PENDING));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(5))
                .andExpect(jsonPath("$.transferAmount").value(250000));

        ArgumentCaptor<TransferRequest> captor = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferService).create(captor.capture());
        assertThat(captor.getValue().transferFrom()).isEqualTo("ACC-001");
        assertThat(captor.getValue().transferTo()).isEqualTo("ACC-002");
        assertThat(captor.getValue().transferAmount()).isEqualTo(250000);
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("idem-1");
    }

    @Test
    void createTransfer_returns409WhenDuplicate() throws Exception {
        String body = """
                {"transferFrom": "ACC-001", "transferTo": "ACC-002", "transferAmount": 250000, "idempotencyKey": "idem-1"}
                """;

        when(transferService.create(any(TransferRequest.class)))
                .thenThrow(new RuntimeException("Duplicate idempotency key"));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$").value("Duplicate idempotency key"));
    }

    // ---- POST validation (via GeneralExceptionHandler) ----

    @Test
    void createTransfer_returns400WhenTransferFromBlank() throws Exception {
        String body = """
                {"transferFrom": "", "transferTo": "ACC-002", "transferAmount": 250000}
                """;

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(transferService, never()).create(any());
    }

    @Test
    void createTransfer_returns400WhenTransferToMissing() throws Exception {
        String body = """
                {"transferFrom": "ACC-001", "transferAmount": 250000}
                """;

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(transferService, never()).create(any());
    }

    @Test
    void createTransfer_returns400WhenAmountMissing() throws Exception {
        String body = """
                {"transferFrom": "ACC-001", "transferTo": "ACC-002"}
                """;

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(transferService, never()).create(any());
    }

    // ---- PUT /transfers/{id} ----

    @Test
    void updateTransfer_returnsMappedResponse() throws Exception {
        String body = """
                {"transferFrom": "ACC-009", "transferTo": "ACC-010", "transferAmount": 99000}
                """;

        when(transferService.update(org.mockito.ArgumentMatchers.eq(1L), any(TransferRequest.class)))
                .thenReturn(transfer(1L, "ACC-009", "ACC-010", 99000, Status.PENDING));

        mockMvc.perform(put("/transfers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(1))
                .andExpect(jsonPath("$.transferFrom").value("ACC-009"))
                .andExpect(jsonPath("$.transferTo").value("ACC-010"))
                .andExpect(jsonPath("$.transferAmount").value(99000));
    }

    @Test
    void updateTransfer_returns404WhenNotFound() throws Exception {
        String body = """
                {"transferFrom": "ACC-009", "transferTo": "ACC-010", "transferAmount": 99000}
                """;

        when(transferService.update(org.mockito.ArgumentMatchers.eq(99L), any(TransferRequest.class)))
                .thenThrow(new RuntimeException("Transfer not found"));

        mockMvc.perform(put("/transfers/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Transfer not found"));
    }

    // ---- DELETE /transfers/{id} ----

    @Test
    void deleteTransfer_returnsMessage() throws Exception {
        mockMvc.perform(delete("/transfers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Transfer deleted"));

        verify(transferService).delete(1L);
    }

    @Test
    void deleteTransfer_returns404WhenServiceFails() throws Exception {
        doThrow(new RuntimeException("Transfer not found")).when(transferService).delete(99L);

        mockMvc.perform(delete("/transfers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Transfer not found"));
    }

    // ---- serialization shape ----

    @Test
    void responseContainsTimestamps() throws Exception {
        Transfer t = transfer(1L, "ACC-001", "ACC-002", 250000, Status.PENDING);
        t.setTransferTime(java.time.LocalDateTime.of(2026, 9, 4, 10, 0));

        when(transferService.getById(1L)).thenReturn(t);

        mockMvc.perform(get("/transfers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferTime[0]").value(2026));
    }
}
