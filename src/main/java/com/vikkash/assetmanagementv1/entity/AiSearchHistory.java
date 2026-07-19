package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One row per AI Search Assistant query that was actually executed.
 * Backs "Recent Searches" / "Popular Searches" / "Pinned Searches" on the
 * AI Search page — all real, DB-backed data (no mock/fake history).
 */
@Entity
@Table(
    name = "ai_search_history",
    indexes = {
        @Index(name = "idx_ai_search_performed_by", columnList = "performedBy"),
        @Index(name = "idx_ai_search_query", columnList = "normalizedQuery")
    }
)
public class AiSearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The raw text the user typed / clicked (a suggestion chip counts too). */
    @Column(nullable = false, length = 500)
    private String query;

    /** Lowercased & trimmed copy of `query`, used to group identical searches for "Popular Searches". */
    @Column(name = "normalized_query", length = 500)
    private String normalizedQuery;

    /** employeeId (EMPLOYEE) or admin username (ADMIN) who ran the search — from the verified JWT. */
    @Column(name = "performed_by", length = 100, nullable = false)
    private String performedBy;

    /** ADMIN or EMPLOYEE, taken from the JWT role at search time. */
    @Column(name = "performed_by_role", length = 30)
    private String performedByRole;

    /** How many assets this search returned (0 shows up as an empty-state search). */
    @Column(name = "result_count")
    private int resultCount;

    /** JSON snapshot of the structured filters that were detected, for display in history ("Brand: Dell, Dept: Finance"). */
    @Column(name = "filters_summary", length = 500)
    private String filtersSummary;

    /** Pinned searches always surface at the top of the user's own history, regardless of recency. */
    @Column(nullable = false)
    private boolean pinned = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AiSearchHistory() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getNormalizedQuery() { return normalizedQuery; }
    public void setNormalizedQuery(String normalizedQuery) { this.normalizedQuery = normalizedQuery; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getPerformedByRole() { return performedByRole; }
    public void setPerformedByRole(String performedByRole) { this.performedByRole = performedByRole; }

    public int getResultCount() { return resultCount; }
    public void setResultCount(int resultCount) { this.resultCount = resultCount; }

    public String getFiltersSummary() { return filtersSummary; }
    public void setFiltersSummary(String filtersSummary) { this.filtersSummary = filtersSummary; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
