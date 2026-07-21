package com.vikkash.assetmanagementv1.dto;

/**
 * Small generic body used by the exit-clearance / complete-resignation /
 * cancel-separation endpoints, which only ever need an optional remarks note.
 */
public class SeparationRemarksRequest {

    private String remarks;

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
