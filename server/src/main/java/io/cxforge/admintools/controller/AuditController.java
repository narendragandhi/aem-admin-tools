package io.cxforge.admintools.controller;

import io.cxforge.admintools.model.AuditLog;
import io.cxforge.admintools.model.AuditLog.AuditAction;
import io.cxforge.admintools.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<Page<AuditLog>> getRecentLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getRecentLogs(page, Math.min(size, 100)));
    }

    @GetMapping("/user/{username}")
    public ResponseEntity<Page<AuditLog>> getLogsByUser(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                auditService.getLogsByUser(username, PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/action/{action}")
    public ResponseEntity<Page<AuditLog>> getLogsByAction(
            @PathVariable AuditAction action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                auditService.getLogsByAction(action, PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/resource/{resourceId}")
    public ResponseEntity<List<AuditLog>> getLogsForResource(@PathVariable String resourceId) {
        return ResponseEntity.ok(auditService.getLogsForResource(resourceId));
    }

    @GetMapping("/range")
    public ResponseEntity<Page<AuditLog>> getLogsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                auditService.getLogsByTimeRange(start, end, PageRequest.of(page, Math.min(size, 100))));
    }
}
