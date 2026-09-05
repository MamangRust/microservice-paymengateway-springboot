package com.transferservice.transferservice.controller;

import com.transferservice.transferservice.dto.TransferMapper;
import com.transferservice.transferservice.dto.TransferRequest;
import com.transferservice.transferservice.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

@RestController @RequestMapping("/transfers")
@Tag(name = "Transfer Management")
@SecurityRequirement(name = "Bearer Authentication")
public class TransferController {
    private final TransferService service;
    private final TransferMapper mapper;
    public TransferController(TransferService service, TransferMapper mapper) { this.service = service; this.mapper = mapper; }

    @GetMapping @Operation(summary = "Get all transfers") public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toResponse).collect(Collectors.toList()));
    }
    @GetMapping("/{id}") @Operation(summary = "Get by ID") public ResponseEntity<?> getById(@PathVariable Long id) {
        try { return ResponseEntity.ok(mapper.toResponse(service.getById(id))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @PostMapping @Operation(summary = "Create transfer") public ResponseEntity<?> create(@Valid @RequestBody TransferRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.create(req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage()); }
    }
    @PutMapping("/{id}") @Operation(summary = "Update transfer") public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody TransferRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.update(id, req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @DeleteMapping("/{id}") @Operation(summary = "Delete transfer") public ResponseEntity<?> delete(@PathVariable Long id) {
        try { service.delete(id); return ResponseEntity.ok("Transfer deleted"); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
}