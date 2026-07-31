package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.AttendanceRecordDTO;
import com.vikkash.assetmanagementv1.service.AttendanceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Employee self-service view of the Attendance Management module: "My
 * Attendance" — the signed-in employee's own punch history only. Mapped
 * under /api/employee/** so the EMPLOYEE/ADMIN role guard applies
 * automatically (SecurityConfig), same pattern as EmployeeSelfController.
 *
 * The JWT subject is always the caller's own employeeId (set at login in
 * AuthController) — we never trust a client-supplied employeeId here, so
 * an employee can only ever see their own punches.
 */
@RestController
@RequestMapping("/api/employee/attendance")
public class EmployeeAttendanceController {

    private final AttendanceService attendanceService;

    public EmployeeAttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public List<AttendanceRecordDTO> myAttendance(Authentication authentication) {
        return attendanceService.getForEmployee(authentication.getName());
    }
}
