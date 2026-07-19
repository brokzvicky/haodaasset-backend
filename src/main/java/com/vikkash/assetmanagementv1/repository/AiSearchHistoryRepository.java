package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.AiSearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiSearchHistoryRepository extends JpaRepository<AiSearchHistory, Long> {

    /** Most recent searches for one user (pinned or not), newest first. */
    List<AiSearchHistory> findByPerformedByOrderByCreatedAtDesc(String performedBy, Pageable pageable);

    /** Just this user's pinned searches. */
    List<AiSearchHistory> findByPerformedByAndPinnedTrueOrderByCreatedAtDesc(String performedBy);

    /**
     * Org-wide "Popular Searches" — groups by the normalized query text across
     * every user (admins see the whole org's habits; employees still benefit
     * from seeing common searches like "expired warranty").
     */
    @Query("""
        SELECT h.normalizedQuery AS query, COUNT(h) AS cnt
        FROM AiSearchHistory h
        WHERE h.normalizedQuery IS NOT NULL AND h.normalizedQuery <> ''
        GROUP BY h.normalizedQuery
        ORDER BY COUNT(h) DESC
        """)
    List<PopularQueryRow> findPopularQueries(Pageable pageable);

    /** A single user's own most-repeated searches (used as a personalized fallback if org-wide is too sparse). */
    @Query("""
        SELECT h.normalizedQuery AS query, COUNT(h) AS cnt
        FROM AiSearchHistory h
        WHERE h.performedBy = :performedBy AND h.normalizedQuery IS NOT NULL AND h.normalizedQuery <> ''
        GROUP BY h.normalizedQuery
        ORDER BY COUNT(h) DESC
        """)
    List<PopularQueryRow> findPopularQueriesForUser(@Param("performedBy") String performedBy, Pageable pageable);

    interface PopularQueryRow {
        String getQuery();
        long getCnt();
    }
}
