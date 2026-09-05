package com.cardservice.cardservice.controller;

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

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.cardservice.cardservice.dto.CardMapper;
import com.cardservice.cardservice.dto.CardMapperImpl;
import com.cardservice.cardservice.dto.CardRequest;
import com.cardservice.cardservice.entity.Card;
import com.cardservice.cardservice.exc.GeneralExceptionHandler;
import com.cardservice.cardservice.service.CardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class CardControllerTest {

    @Mock
    private CardService cardService;

    private MockMvc mockMvc;

    private final CardMapper cardMapper = new CardMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        CardController controller = new CardController(cardService, cardMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Card createCard(Long cardId, Integer userId, String cardNumber) {
        Card card = new Card();
        card.setCardId(cardId);
        card.setUserId(userId);
        card.setCardNumber(cardNumber);
        card.setCardType("CREDIT");
        card.setExpireDate(LocalDate.of(2027, 12, 31));
        card.setCvv("123");
        card.setCardProvider("VISA");
        card.setStatus(com.cardservice.cardservice.entity.CardStatus.ACTIVE);
        card.setCreditLimit(new BigDecimal("5000.00"));
        card.setPoints(new BigDecimal("10.00"));
        card.setCreatedAt(LocalDateTime.of(2026, 9, 4, 10, 0));
        return card;
    }

    // ---- GET /cards ----

    @Test
    void getAllCards_returnsMappedList() throws Exception {
        when(cardService.getAllCards()).thenReturn(List.of(createCard(1L, 1, "4111111111111111")));

        mockMvc.perform(get("/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardId").value(1))
                .andExpect(jsonPath("$[0].cardNumber").value("4111111111111111"))
                .andExpect(jsonPath("$[0].cardProvider").value("VISA"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void getAllCards_responseOmitsCvv() throws Exception {
        // CardResponse has no cvv field — CVV must never leak through the API
        when(cardService.getAllCards()).thenReturn(List.of(createCard(1L, 1, "4111111111111111")));

        mockMvc.perform(get("/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cvv").doesNotExist());
    }

    @Test
    void getAllCards_returnsEmptyListWhenNone() throws Exception {
        when(cardService.getAllCards()).thenReturn(List.of());

        mockMvc.perform(get("/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- GET /cards/{id} (in-handler try/catch -> 404 raw message) ----

    @Test
    void getCardById_returnsResponse() throws Exception {
        when(cardService.getCardById(1L)).thenReturn(createCard(1L, 1, "4111111111111111"));

        mockMvc.perform(get("/cards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(1))
                .andExpect(jsonPath("$.cardNumber").value("4111111111111111"));
    }

    @Test
    void getCardById_returns404WithRawMessageWhenNotFound() throws Exception {
        when(cardService.getCardById(99L)).thenThrow(new RuntimeException("Card not found"));

        // in-handler catch: body is the raw message string, not ErrorResponse
        mockMvc.perform(get("/cards/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Card not found"));
    }

    // ---- GET /cards/by-number/{cardNumber} (in-handler try/catch -> 404) ----

    @Test
    void getCardByNumber_returnsResponse() throws Exception {
        when(cardService.getByCardNumber("4111111111111111"))
                .thenReturn(createCard(1L, 1, "4111111111111111"));

        mockMvc.perform(get("/cards/by-number/4111111111111111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(1))
                .andExpect(jsonPath("$.cardNumber").value("4111111111111111"));
    }

    @Test
    void getCardByNumber_returns404WithRawMessageWhenNotFound() throws Exception {
        when(cardService.getByCardNumber("nope")).thenThrow(new RuntimeException("Card not found"));

        mockMvc.perform(get("/cards/by-number/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Card not found"));
    }

    // ---- GET /cards/by-user/{userId} ----

    @Test
    void getCardsByUser_returnsMappedList() throws Exception {
        when(cardService.getByUserId(7)).thenReturn(List.of(createCard(3L, 7, "4333333333333333")));

        mockMvc.perform(get("/cards/by-user/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardId").value(3))
                .andExpect(jsonPath("$[0].userId").value(7));
    }

    @Test
    void getCardsByUser_returnsEmptyListWhenNoMatch() throws Exception {
        when(cardService.getByUserId(42)).thenReturn(List.of());

        mockMvc.perform(get("/cards/by-user/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- POST /cards ----

    @Test
    void createCard_returnsCreatedCard() throws Exception {
        CardRequest request = new CardRequest(1, "4111111111111111", "CREDIT",
                LocalDate.of(2027, 12, 31), "123", "VISA",
                new BigDecimal("5000.00"), new BigDecimal("10.00"));

        when(cardService.createCard(any(CardRequest.class)))
                .thenReturn(createCard(5L, 1, "4111111111111111"));

        mockMvc.perform(post("/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(5))
                .andExpect(jsonPath("$.cardNumber").value("4111111111111111"));
    }

    @Test
    void createCard_responseOmitsCvv() throws Exception {
        CardRequest request = new CardRequest(1, "4111111111111111", "CREDIT",
                LocalDate.of(2027, 12, 31), "123", "VISA",
                new BigDecimal("5000.00"), new BigDecimal("10.00"));

        when(cardService.createCard(any(CardRequest.class)))
                .thenReturn(createCard(5L, 1, "4111111111111111"));

        mockMvc.perform(post("/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvv").doesNotExist());
    }

    @Test
    void createCard_returns400WhenCardNumberBlank() throws Exception {
        CardRequest request = new CardRequest(1, " ", "CREDIT", null, "123", null, null, null);

        mockMvc.perform(post("/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(cardService, never()).createCard(any(CardRequest.class));
    }

    @Test
    void createCard_returns400WhenUserIdNull() throws Exception {
        String body = "{\"userId\": null, \"cardNumber\": \"4111111111111111\"}";

        mockMvc.perform(post("/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(cardService, never()).createCard(any(CardRequest.class));
    }

    // ---- PUT /cards/{id} (in-handler try/catch -> 404) ----

    @Test
    void updateCard_returnsUpdatedResponse() throws Exception {
        CardRequest request = new CardRequest(1, "4111111111111111", "DEBIT",
                LocalDate.of(2029, 6, 30), "123", "MASTERCARD",
                new BigDecimal("8000.00"), new BigDecimal("55.00"));

        Card updated = createCard(1L, 1, "4111111111111111");
        updated.setCardType("DEBIT");
        updated.setCardProvider("MASTERCARD");
        when(cardService.updateCard(eq(1L), any(CardRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/cards/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardType").value("DEBIT"))
                .andExpect(jsonPath("$.cardProvider").value("MASTERCARD"));
    }

    @Test
    void updateCard_returns404WithRawMessageWhenNotFound() throws Exception {
        CardRequest request = new CardRequest(1, "4111111111111111", "CREDIT", null, null, null, null, null);

        when(cardService.updateCard(eq(99L), any(CardRequest.class)))
                .thenThrow(new RuntimeException("Card not found"));

        mockMvc.perform(put("/cards/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Card not found"));
    }

    // ---- DELETE /cards/{id} (in-handler try/catch) ----

    @Test
    void deleteCard_returnsSuccessMessage() throws Exception {
        mockMvc.perform(delete("/cards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Card deleted"));

        verify(cardService).deleteCard(1L);
    }

    @Test
    void deleteCard_returns404WithRawMessageWhenServiceFails() throws Exception {
        doThrow(new RuntimeException("Card not found")).when(cardService).deleteCard(99L);

        mockMvc.perform(delete("/cards/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Card not found"));
    }

    // ---- serialization shape ----

    @Test
    void cardResponse_serializesLocalDateAndTimestamps() throws Exception {
        when(cardService.getCardById(1L)).thenReturn(createCard(1L, 1, "4111111111111111"));

        mockMvc.perform(get("/cards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expireDate[0]").value(2027))
                .andExpect(jsonPath("$.createdAt[0]").value(2026));
    }
}
