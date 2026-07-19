package com.vikkash.assetmanagementv1.dto;

import com.vikkash.assetmanagementv1.entity.AiSearchHistory;

import java.time.LocalDateTime;

/** Lightweight projection of AiSearchHistory returned to the frontend. */
public class AiSearchHistoryDTO {

    private Long id;
    private String query;
    private int resultCount;
    private String filtersSummary;
    private boolean pinned;
    private LocalDateTime createdAt;

    public static AiSearchHistoryDTO from(AiSearchHistory h) {
        AiSearchHistoryDTO dto = new AiSearchHistoryDTO();
        dto.id = h.getId();
        dto.query = h.getQuery();
        dto.resultCount = h.getResultCount();
        dto.filtersSummary = h.getFiltersSummary();
        dto.pinned = h.isPinned();
        dto.createdAt = h.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public int getResultCount() { return resultCount; }
    public void setResultCount(int resultCount) { this.resultCount = resultCount; }

    public String getFiltersSummary() { return filtersSummary; }
    public void setFiltersSummary(String filtersSummary) { this.filtersSummary = filtersSummary; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
