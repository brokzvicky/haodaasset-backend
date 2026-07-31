package com.vikkash.assetmanagementv1.dto;

/** Today's KPI summary shown on the Attendance Management page and (optionally) the main Dashboard. */
public class AttendanceStatsDTO {

    private long totalPunchesToday;
    private long inPunchesToday;
    private long outPunchesToday;
    private long unmappedPunchesToday;
    private long employeesPresentToday;
    private long devicesOnline;
    private long devicesTotal;
    private long unmappedDevicePins;

    public long getTotalPunchesToday() { return totalPunchesToday; }
    public void setTotalPunchesToday(long v) { this.totalPunchesToday = v; }

    public long getInPunchesToday() { return inPunchesToday; }
    public void setInPunchesToday(long v) { this.inPunchesToday = v; }

    public long getOutPunchesToday() { return outPunchesToday; }
    public void setOutPunchesToday(long v) { this.outPunchesToday = v; }

    public long getUnmappedPunchesToday() { return unmappedPunchesToday; }
    public void setUnmappedPunchesToday(long v) { this.unmappedPunchesToday = v; }

    public long getEmployeesPresentToday() { return employeesPresentToday; }
    public void setEmployeesPresentToday(long v) { this.employeesPresentToday = v; }

    public long getDevicesOnline() { return devicesOnline; }
    public void setDevicesOnline(long v) { this.devicesOnline = v; }

    public long getDevicesTotal() { return devicesTotal; }
    public void setDevicesTotal(long v) { this.devicesTotal = v; }

    public long getUnmappedDevicePins() { return unmappedDevicePins; }
    public void setUnmappedDevicePins(long v) { this.unmappedDevicePins = v; }
}
