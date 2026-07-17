package com.vikkash.assetmanagementv1.dto;

public class SnoozeRequest {

    /** Minutes from now to snooze until. Defaults to 60 (1 hour) if not provided. */
    private Integer minutes;

    public Integer getMinutes() { return minutes; }
    public void setMinutes(Integer minutes) { this.minutes = minutes; }
}
