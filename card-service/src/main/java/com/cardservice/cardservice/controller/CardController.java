package com.cardservice.cardservice.controller;

import com.cardservice.cardservice.dto.CardMapper;
import com.cardservice.cardservice.dto.CardRequest;
import com.cardservice.cardservice.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/cards")
@Tag(name = "Card Management")
@SecurityRequirement(name = "Bearer Authentication")
public class CardController {
    private final CardService service;
    private final CardMapper mapper;
    public CardController(CardService service, CardMapper mapper) { this.service = service; this.mapper = mapper; }

    @GetMapping @Operation(summary = "Get all cards") public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(service.getAllCards().stream().map(mapper::toResponse).collect(Collectors.toList()));
    }
    @GetMapping("/{id}") @Operation(summary = "Get card by ID") public ResponseEntity<?> getById(@PathVariable Long id) {
        try { return ResponseEntity.ok(mapper.toResponse(service.getCardById(id))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @GetMapping("/by-number/{cardNumber}") @Operation(summary = "Get card by number") public ResponseEntity<?> getByNumber(@PathVariable String cardNumber) {
        try { return ResponseEntity.ok(mapper.toResponse(service.getByCardNumber(cardNumber))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @GetMapping("/by-user/{userId}") @Operation(summary = "Get cards by user") public ResponseEntity<?> getByUser(@PathVariable Integer userId) {
        return ResponseEntity.ok(service.getByUserId(userId).stream().map(mapper::toResponse).collect(Collectors.toList()));
    }
    @PostMapping @Operation(summary = "Create card") public ResponseEntity<?> create(@Valid @RequestBody CardRequest req) {
        return ResponseEntity.ok(mapper.toResponse(service.createCard(req)));
    }
    @PutMapping("/{id}") @Operation(summary = "Update card") public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody CardRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.updateCard(id, req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @DeleteMapping("/{id}") @Operation(summary = "Delete card") public ResponseEntity<?> delete(@PathVariable Long id) {
        try { service.deleteCard(id); return ResponseEntity.ok("Card deleted"); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
}