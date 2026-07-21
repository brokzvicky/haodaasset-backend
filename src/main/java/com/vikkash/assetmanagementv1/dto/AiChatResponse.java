package com.vikkash.assetmanagementv1.dto;

import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.MaintenanceRecord;

import java.util.List;

/**
 * Response body for POST /api/ai/chat.
 *
 * `answer` is always populated with a plain-English sentence. `type` tells
 * the frontend which of the optional structured fields (if any) to render
 * as cards underneath the answer bubble.
 */
public class AiChatResponse {

    /** ASSETS | EMPLOYEES | MAINTENANCE | INFO */
    private String type = "INFO";

    private String answer;
    private List<Asset> assets;
    private List<AiChatEmployeeStat> employees;
    private List<MaintenanceRecord> maintenanceRecords;
    private List<String> suggestions;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<Asset> getAssets() { return assets; }
    public void setAssets(List<Asset> assets) { this.assets = assets; }

    public List<AiChatEmployeeStat> getEmployees() { return employees; }
    public void setEmployees(List<AiChatEmployeeStat> employees) { this.employees = employees; }

    public List<MaintenanceRecord> getMaintenanceRecords() { return maintenanceRecords; }
    public void setMaintenanceRecords(List<MaintenanceRecord> maintenanceRecords) { this.maintenanceRecords = maintenanceRecords; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
}
