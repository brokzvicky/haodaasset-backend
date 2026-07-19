package com.vikkash.assetmanagementv1.dto;

import com.vikkash.assetmanagementv1.entity.Asset;

import java.util.List;
import java.util.Map;

/** Response body for POST /api/ai/search. */
public class AiSearchResponse {

    private String summary;
    private AiSearchFilters filters;
    private List<String> matchedTerms;
    private List<String> ignoredTerms;
    private List<Asset> results;
    private long resultCount;
    private int page;
    private int size;
    private int totalPages;
    private Map<String, Long> statusBreakdown;

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public AiSearchFilters getFilters() { return filters; }
    public void setFilters(AiSearchFilters filters) { this.filters = filters; }

    public List<String> getMatchedTerms() { return matchedTerms; }
    public void setMatchedTerms(List<String> matchedTerms) { this.matchedTerms = matchedTerms; }

    public List<String> getIgnoredTerms() { return ignoredTerms; }
    public void setIgnoredTerms(List<String> ignoredTerms) { this.ignoredTerms = ignoredTerms; }

    public List<Asset> getResults() { return results; }
    public void setResults(List<Asset> results) { this.results = results; }

    public long getResultCount() { return resultCount; }
    public void setResultCount(long resultCount) { this.resultCount = resultCount; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public Map<String, Long> getStatusBreakdown() { return statusBreakdown; }
    public void setStatusBreakdown(Map<String, Long> statusBreakdown) { this.statusBreakdown = statusBreakdown; }
}
