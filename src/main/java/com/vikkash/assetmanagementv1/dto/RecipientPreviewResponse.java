package com.vikkash.assetmanagementv1.dto;

import java.util.List;

/** Response for the recipient-count preview used by the Share File confirmation modal. */
public class RecipientPreviewResponse {

    private int count;
    private String summary;
    private List<String> sampleNames;

    public RecipientPreviewResponse() {
    }

    public RecipientPreviewResponse(int count, String summary, List<String> sampleNames) {
        this.count = count;
        this.summary = summary;
        this.sampleNames = sampleNames;
    }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<String> getSampleNames() { return sampleNames; }
    public void setSampleNames(List<String> sampleNames) { this.sampleNames = sampleNames; }
}
