package com.saldoservice.saldoservice.controller;

import com.saldoservice.saldoservice.dto.SaldoMapper;
import com.saldoservice.saldoservice.dto.SaldoRequest;
import com.saldoservice.saldoservice.service.SaldoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/saldos")
@Tag(name = "Saldo Management")
@SecurityRequirement(name = "Bearer Authentication")
public class SaldoController {
    private final SaldoService service;
    private final SaldoMapper mapper;
    public SaldoController(SaldoService service, SaldoMapper mapper) { this.service = service; this.mapper = mapper; }

    @GetMapping @Operation(summary = "Get all saldos") public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toResponse).collect(Collectors.toList()));
    }
    @GetMapping("/{id}") @Operation(summary = "Get saldo by ID") public ResponseEntity<?> getById(@PathVariable Long id) {
        try { return ResponseEntity.ok(mapper.toResponse(service.getById(id))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @GetMapping("/card/{cardNumber}") @Operation(summary = "Get saldo by card number") public ResponseEntity<?> getByCard(@PathVariable String cardNumber) {
        try { return ResponseEntity.ok(mapper.toResponse(service.getByCardNumber(cardNumber))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @PostMapping @Operation(summary = "Create saldo") public ResponseEntity<?> create(@Valid @RequestBody SaldoRequest req) {
        return ResponseEntity.ok(mapper.toResponse(service.create(req)));
    }
    @PutMapping("/{id}") @Operation(summary = "Update saldo") public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody SaldoRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.update(id, req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @DeleteMapping("/{id}") @Operation(summary = "Delete saldo") public ResponseEntity<?> delete(@PathVariable Long id) {
        try { service.delete(id); return ResponseEntity.ok("Saldo deleted"); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
}