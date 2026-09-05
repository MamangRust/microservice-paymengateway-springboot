package com.merchant.merchant.controller;

import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.merchant.merchant.dto.MerchantMapper;
import com.merchant.merchant.dto.MerchantMapperImpl;
import com.merchant.merchant.dto.MerchantRequest;
import com.merchant.merchant.entity.Merchant;
import com.merchant.merchant.exc.GeneralExceptionHandler;
import com.merchant.merchant.service.MerchantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class MerchantControllerTest {

    @Mock
    private MerchantService merchantService;

    private MockMvc mockMvc;

    private final MerchantMapper merchantMapper = new MerchantMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        MerchantController controller = new MerchantController(merchantService, merchantMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Merchant createMerchant(Long id, String name) {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(id);
        merchant.setUserId(1L);
        merchant.setMerchantNo("MNO-" + id);
        merchant.setApiKey("KEY-" + id);
        merchant.setName(name);
        return merchant;
    }

    @Test
    void getAll_returnsMappedList() throws Exception {
        when(merchantService.getAll()).thenReturn(List.of(createMerchant(1L, "Merchant1")));

        mockMvc.perform(get("/merchants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].merchantId").value(1))
                .andExpect(jsonPath("$[0].name").value("Merchant1"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getAll_returnsEmptyListWhenNone() throws Exception {
        when(merchantService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/merchants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getById_returnsResponse() throws Exception {
        when(merchantService.getById(1L)).thenReturn(createMerchant(1L, "Merchant1"));

        mockMvc.perform(get("/merchants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(1))
                .andExpect(jsonPath("$.name").value("Merchant1"))
                .andExpect(jsonPath("$.merchantNo").value("MNO-1"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(merchantService.getById(99L)).thenThrow(new RuntimeException("Merchant not found"));

        mockMvc.perform(get("/merchants/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Merchant not found"));
    }

    @Test
    void create_returnsCreatedResponse() throws Exception {
        MerchantRequest request = createRequest("NewMerchant");

        when(merchantService.create(any(MerchantRequest.class))).thenReturn(createMerchant(5L, "NewMerchant"));

        mockMvc.perform(post("/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(5))
                .andExpect(jsonPath("$.name").value("NewMerchant"));
    }

    @Test
    void create_returns409WhenDuplicateEntry() throws Exception {
        MerchantRequest request = createRequest("DupMerchant");

        when(merchantService.create(any(MerchantRequest.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        mockMvc.perform(post("/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Duplicate entry"));
    }

    @Test
    void create_returns500WhenServiceFails() throws Exception {
        MerchantRequest request = createRequest("BrokenMerchant");

        when(merchantService.create(any(MerchantRequest.class)))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post("/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Error"))
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }

    @Test
    void create_returns400WhenNameBlank() throws Exception {
        MerchantRequest request = createRequest(" ");

        mockMvc.perform(post("/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(merchantService, never()).create(any(MerchantRequest.class));
    }

    @Test
    void create_returns400WhenNameTooLong() throws Exception {
        MerchantRequest request = createRequest("M".repeat(101));

        mockMvc.perform(post("/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(merchantService, never()).create(any(MerchantRequest.class));
    }

    @Test
    void update_returnsUpdatedResponse() throws Exception {
        MerchantRequest request = createRequest("UpdatedName");

        when(merchantService.update(org.mockito.ArgumentMatchers.eq(1L), any(MerchantRequest.class)))
                .thenReturn(createMerchant(1L, "UpdatedName"));

        mockMvc.perform(put("/merchants/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(1))
                .andExpect(jsonPath("$.name").value("UpdatedName"));
    }

    @Test
    void update_returns404WhenNotFound() throws Exception {
        MerchantRequest request = createRequest("UpdatedName");

        when(merchantService.update(org.mockito.ArgumentMatchers.eq(99L), any(MerchantRequest.class)))
                .thenThrow(new RuntimeException("Merchant not found"));

        mockMvc.perform(put("/merchants/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Merchant not found"));
    }

    @Test
    void delete_returnsSuccessMessage() throws Exception {
        mockMvc.perform(delete("/merchants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Merchant deleted"));

        verify(merchantService).delete(1L);
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("Merchant not found")).when(merchantService).delete(99L);

        mockMvc.perform(delete("/merchants/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Merchant not found"));
    }

    @Test
    void responseContainsTimestamps() throws Exception {
        Merchant merchant = createMerchant(1L, "Merchant1");
        merchant.setCreatedAt(LocalDateTime.of(2026, 9, 4, 10, 0));
        merchant.setUpdatedAt(LocalDateTime.of(2026, 9, 4, 11, 30));

        when(merchantService.getById(1L)).thenReturn(merchant);

        mockMvc.perform(get("/merchants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt[0]").value(2026))
                .andExpect(jsonPath("$.updatedAt[0]").value(2026));
    }

    private MerchantRequest createRequest(String name) {
        return new MerchantRequest(name, "desc", "addr", "mail@mail.com", "0812000");
    }
}
