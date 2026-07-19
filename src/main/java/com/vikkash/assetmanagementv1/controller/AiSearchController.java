package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.AiSearchFacetsDTO;
import com.vikkash.assetmanagementv1.dto.AiSearchHistoryDTO;
import com.vikkash.assetmanagementv1.dto.AiSearchRequest;
import com.vikkash.assetmanagementv1.dto.AiSearchResponse;
import com.vikkash.assetmanagementv1.entity.AiSearchHistory;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AiSearchHistoryRepository;
import com.vikkash.assetmanagementv1.service.AiSearchService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The AI Search Assistant API. Everything here requires a valid JWT
 * (ROLE_ADMIN or ROLE_EMPLOYEE — see SecurityConfig). Employees are always
 * scoped to their own assets inside AiSearchService, no matter what filters
 * their query text implies.
 */
@RestController
@RequestMapping("/api/ai/search")
public class AiSearchController {

    private final AiSearchService aiSearchService;
    private final AiSearchHistoryRepository historyRepository;

    public AiSearchController(AiSearchService aiSearchService, AiSearchHistoryRepository historyRepository) {
        this.aiSearchService = aiSearchService;
        this.historyRepository = historyRepository;
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    @PostMapping
    public ResponseEntity<AiSearchResponse> search(@Valid @RequestBody AiSearchRequest request,
                                                     Authentication authentication) {
        boolean admin = isAdmin(authentication);
        String callerId = authentication.getName();
        String role = admin ? "ADMIN" : "EMPLOYEE";
        AiSearchResponse response = aiSearchService.search(
                request.getQuery(), admin, callerId, role, request.getPage(), request.getSize());
        aiSearchService.recordHistory(request.getQuery(), callerId, role,
                (int) response.getResultCount(), response.getMatchedTerms());
        return ResponseEntity.ok(response);
    }

    /** Live vocab (brands/categories/departments/branches/statuses) for the "Smart Suggestions" panel. */
    @GetMapping("/facets")
    public ResponseEntity<AiSearchFacetsDTO> facets(Authentication authentication) {
        return ResponseEntity.ok(aiSearchService.facets(isAdmin(authentication)));
    }

    // ── Search history: recent / pinned / popular ────────────────────────────

    @GetMapping("/history")
    public List<AiSearchHistoryDTO> recentHistory(Authentication authentication,
                                                    @RequestParam(defaultValue = "15") int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(limit, 50));
        return historyRepository.findByPerformedByOrderByCreatedAtDesc(authentication.getName(), pageable)
                .stream().map(AiSearchHistoryDTO::from).collect(Collectors.toList());
    }

    @GetMapping("/history/pinned")
    public List<AiSearchHistoryDTO> pinnedHistory(Authentication authentication) {
        return historyRepository.findByPerformedByAndPinnedTrueOrderByCreatedAtDesc(authentication.getName())
                .stream().map(AiSearchHistoryDTO::from).collect(Collectors.toList());
    }

    /** Org-wide popular searches (falls back to the caller's own repeat searches if the org list is thin). */
    @GetMapping("/popular")
    public List<Map<String, Object>> popular(Authentication authentication,
                                               @RequestParam(defaultValue = "8") int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(limit, 20));
        var rows = historyRepository.findPopularQueries(pageable);
        if (rows.isEmpty()) {
            rows = historyRepository.findPopularQueriesForUser(authentication.getName(), pageable);
        }
        return rows.stream()
                .map(r -> Map.<String, Object>of("query", r.getQuery(), "count", r.getCnt()))
                .collect(Collectors.toList());
    }

    @PutMapping("/history/{id}/pin")
    public ResponseEntity<AiSearchHistoryDTO> togglePin(@PathVariable Long id, Authentication authentication) {
        AiSearchHistory h = historyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Search history entry not found"));
        if (!h.getPerformedBy().equalsIgnoreCase(authentication.getName()) && !isAdmin(authentication)) {
            throw new AccessDeniedException("You may only manage your own search history.");
        }
        h.setPinned(!h.isPinned());
        historyRepository.save(h);
        return ResponseEntity.ok(AiSearchHistoryDTO.from(h));
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<Map<String, String>> deleteHistory(@PathVariable Long id, Authentication authentication) {
        AiSearchHistory h = historyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Search history entry not found"));
        if (!h.getPerformedBy().equalsIgnoreCase(authentication.getName()) && !isAdmin(authentication)) {
            throw new AccessDeniedException("You may only manage your own search history.");
        }
        historyRepository.delete(h);
        return ResponseEntity.ok(Map.of("message", "Search history entry deleted"));
    }
}
