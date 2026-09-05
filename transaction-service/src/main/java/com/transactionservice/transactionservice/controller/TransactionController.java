package com.transactionservice.transactionservice.controller;

import com.transactionservice.transactionservice.dto.TransactionMapper;
import com.transactionservice.transactionservice.dto.TransactionRequest;
import com.transactionservice.transactionservice.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

@RestController @RequestMapping("/transactions")
@Tag(name = "Transaction Management")
@SecurityRequirement(name = "Bearer Authentication")
public class TransactionController {
    private final TransactionService service;
    private final TransactionMapper mapper;
    public TransactionController(TransactionService service, TransactionMapper mapper) { this.service = service; this.mapper = mapper; }

    @GetMapping @Operation(summary = "Get all transactions") public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toResponse).collect(Collectors.toList()));
    }
    @GetMapping("/{id}") @Operation(summary = "Get by ID") public ResponseEntity<?> getById(@PathVariable Long id) {
        try { return ResponseEntity.ok(mapper.toResponse(service.getById(id))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @PostMapping @Operation(summary = "Create transaction") public ResponseEntity<?> create(@Valid @RequestBody TransactionRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.create(req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage()); }
    }
    @PutMapping("/{id}") @Operation(summary = "Update transaction") public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody TransactionRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.update(id, req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @DeleteMapping("/{id}") @Operation(summary = "Delete transaction") public ResponseEntity<?> delete(@PathVariable Long id) {
        try { service.delete(id); return ResponseEntity.ok("Transaction deleted"); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
}