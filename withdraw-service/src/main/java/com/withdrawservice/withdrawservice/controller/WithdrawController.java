package com.withdrawservice.withdrawservice.controller;

import com.withdrawservice.withdrawservice.dto.WithdrawMapper;
import com.withdrawservice.withdrawservice.dto.WithdrawRequest;
import com.withdrawservice.withdrawservice.service.WithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

@RestController @RequestMapping("/withdraws")
@Tag(name = "Withdraw Management")
@SecurityRequirement(name = "Bearer Authentication")
public class WithdrawController {
    private final WithdrawService service;
    private final WithdrawMapper mapper;
    public WithdrawController(WithdrawService service, WithdrawMapper mapper) { this.service = service; this.mapper = mapper; }

    @GetMapping @Operation(summary = "Get all withdraws") public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toResponse).collect(Collectors.toList()));
    }
    @GetMapping("/{id}") @Operation(summary = "Get by ID") public ResponseEntity<?> getById(@PathVariable Long id) {
        try { return ResponseEntity.ok(mapper.toResponse(service.getById(id))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @PostMapping @Operation(summary = "Create withdraw") public ResponseEntity<?> create(@Valid @RequestBody WithdrawRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.create(req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage()); }
    }
    @PutMapping("/{id}") @Operation(summary = "Update withdraw") public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody WithdrawRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.update(id, req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @DeleteMapping("/{id}") @Operation(summary = "Delete withdraw") public ResponseEntity<?> delete(@PathVariable Long id) {
        try { service.delete(id); return ResponseEntity.ok("Withdraw deleted"); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
}