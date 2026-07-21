package com.vikkash.assetmanagementv1.dto;

/** A single employee + how many assets they currently hold, used by chat answers like "who has the most assets". */
public class AiChatEmployeeStat {

    private String employeeId;
    private String employeeName;
    private String department;
    private long assetCount;

    public AiChatEmployeeStat() {}

    public AiChatEmployeeStat(String employeeId, String employeeName, String department, long assetCount) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.department = department;
        this.assetCount = assetCount;
    }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public long getAssetCount() { return assetCount; }
    public void setAssetCount(long assetCount) { this.assetCount = assetCount; }
}
