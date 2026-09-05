package com.topupservice.topupservice.controller;

import com.topupservice.topupservice.dto.TopupMapper;
import com.topupservice.topupservice.dto.TopupRequest;
import com.topupservice.topupservice.service.TopupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

@RestController @RequestMapping("/topups")
@Tag(name = "Topup Management")
@SecurityRequirement(name = "Bearer Authentication")
public class TopupController {
    private final TopupService service;
    private final TopupMapper mapper;
    public TopupController(TopupService service, TopupMapper mapper) { this.service = service; this.mapper = mapper; }

    @GetMapping @Operation(summary = "Get all topups") public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toResponse).collect(Collectors.toList()));
    }
    @GetMapping("/{id}") @Operation(summary = "Get by ID") public ResponseEntity<?> getById(@PathVariable Long id) {
        try { return ResponseEntity.ok(mapper.toResponse(service.getById(id))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @PostMapping @Operation(summary = "Create topup") public ResponseEntity<?> create(@Valid @RequestBody TopupRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.create(req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage()); }
    }
    @PutMapping("/{id}") @Operation(summary = "Update topup") public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody TopupRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.update(id, req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
    @DeleteMapping("/{id}") @Operation(summary = "Delete topup") public ResponseEntity<?> delete(@PathVariable Long id) {
        try { service.delete(id); return ResponseEntity.ok("Topup deleted"); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
}