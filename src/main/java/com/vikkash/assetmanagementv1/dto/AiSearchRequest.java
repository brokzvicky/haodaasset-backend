package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/ai/search.
 * `page`/`size` are optional (defaults applied in the controller) so simple
 * clients can omit them entirely.
 */
public class AiSearchRequest {

    @NotBlank(message = "query is required")
    private String query;

    private Integer page;
    private Integer size;

    public AiSearchRequest() {}

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}
