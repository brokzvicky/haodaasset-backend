package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.TaskRequest;
import com.vikkash.assetmanagementv1.entity.Task;
import com.vikkash.assetmanagementv1.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Haoda Pulse: task management. Mapped under /api/admin/** so the ADMIN
 * role guard applies automatically (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/pulse/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<Task> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public Task getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public ResponseEntity<Task> create(@Valid @RequestBody TaskRequest request, Authentication authentication) {
        String createdBy = authentication != null ? authentication.getName() : "Admin";
        return ResponseEntity.status(201).body(service.create(request, createdBy));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Task> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.updateStatus(id, body.get("status")));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<Task> complete(@PathVariable Long id) {
        return ResponseEntity.ok(service.updateStatus(id, "Completed"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Task deleted successfully"));
    }
}
