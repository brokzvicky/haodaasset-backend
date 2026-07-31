package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.AttendanceMappingRequest;
import com.vikkash.assetmanagementv1.dto.AttendanceMappingResponse;
import com.vikkash.assetmanagementv1.dto.AttendanceRecordDTO;
import com.vikkash.assetmanagementv1.dto.AttendanceStatsDTO;
import com.vikkash.assetmanagementv1.entity.AttendanceDevice;
import com.vikkash.assetmanagementv1.service.AttendanceEventPublisher;
import com.vikkash.assetmanagementv1.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Attendance Management: admin-facing API — punch history, today's KPI
 * stats, live SSE feed, registered devices, and device-PIN-to-employee
 * mappings. Mapped under /api/admin/** so the ADMIN role guard applies
 * automatically (SecurityConfig), same pattern as every other admin module
 * (Maintenance, Haoda Pulse, Network Credentials, etc.).
 */
@RestController
@RequestMapping("/api/admin/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceEventPublisher eventPublisher;

    public AttendanceController(AttendanceService attendanceService, AttendanceEventPublisher eventPublisher) {
        this.attendanceService = attendanceService;
        this.eventPublisher = eventPublisher;
    }

    // ── Records ──────────────────────────────────────────────────────────

    /** Most recent 200 punches, newest first — used for the page's initial load; live updates arrive over /stream after that. */
    @GetMapping
    public List<AttendanceRecordDTO> getRecent() {
        return attendanceService.getRecent();
    }

    /** Full punch history, no limit — used for exports / the "view all" toggle. */
    @GetMapping("/all")
    public List<AttendanceRecordDTO> getAll() {
        return attendanceService.getAll();
    }

    @GetMapping("/employee/{employeeId}")
    public List<AttendanceRecordDTO> getForEmployee(@PathVariable String employeeId) {
        return attendanceService.getForEmployee(employeeId);
    }

    @GetMapping("/range")
    public List<AttendanceRecordDTO> getForRange(@RequestParam("from") String from, @RequestParam("to") String to) {
        return attendanceService.getForDateRange(LocalDate.parse(from), LocalDate.parse(to));
    }

    /** Server-Sent Events stream — the frontend opens one EventSource connection here and gets pushed each new punch. */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream() {
        return eventPublisher.subscribe();
    }

    // ── Stats ────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public AttendanceStatsDTO getStats() {
        return attendanceService.getStats();
    }

    // ── Devices ──────────────────────────────────────────────────────────

    @GetMapping("/devices")
    public List<AttendanceDevice> getDevices() {
        return attendanceService.getDevices();
    }

    @PutMapping("/devices/{id}")
    public ResponseEntity<AttendanceDevice> renameDevice(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(attendanceService.renameDevice(id, body.get("deviceName"), body.get("location")));
    }

    // ── Device PIN -> Employee mappings ─────────────────────────────────

    @GetMapping("/mappings")
    public List<AttendanceMappingResponse> getMappings() {
        return attendanceService.getMappings();
    }

    @PostMapping("/mappings")
    public ResponseEntity<AttendanceMappingResponse> createMapping(@Valid @RequestBody AttendanceMappingRequest req) {
        return ResponseEntity.status(201).body(attendanceService.createMapping(req));
    }

    @PutMapping("/mappings/{id}")
    public ResponseEntity<AttendanceMappingResponse> updateMapping(@PathVariable Long id, @Valid @RequestBody AttendanceMappingRequest req) {
        return ResponseEntity.ok(attendanceService.updateMapping(id, req));
    }

    @DeleteMapping("/mappings/{id}")
    public ResponseEntity<Map<String, String>> deleteMapping(@PathVariable Long id) {
        attendanceService.deleteMapping(id);
        return ResponseEntity.ok(Map.of("message", "Mapping deleted successfully"));
    }
}
