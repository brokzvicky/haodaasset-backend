package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    /** Newest punches first — what the Attendance Management page displays by default. */
    List<AttendanceRecord> findAllByOrderByPunchTimeDesc();

    /** Newest punches first, most recent N — used for the live feed's initial page load. */
    List<AttendanceRecord> findTop200ByOrderByPunchTimeDesc();

    Optional<AttendanceRecord> findByDeviceSerialNumberAndDeviceUserIdAndPunchTime(
            String deviceSerialNumber, String deviceUserId, LocalDateTime punchTime);

    long countByDeviceUserIdAndPunchTimeBetween(
            String deviceUserId, LocalDateTime startInclusive, LocalDateTime endExclusive);

    /** Full attendance history for one employee (self-service "My Attendance" view), newest first. */
    List<AttendanceRecord> findByEmployeeIdOrderByPunchTimeDesc(String employeeId);

    List<AttendanceRecord> findByPunchTimeBetweenOrderByPunchTimeDesc(LocalDateTime start, LocalDateTime end);

    long countByPunchTimeBetween(LocalDateTime start, LocalDateTime end);

    long countByPunchTypeAndPunchTimeBetween(String punchType, LocalDateTime start, LocalDateTime end);

    long countByEmployeeIdIsNullAndPunchTimeBetween(LocalDateTime start, LocalDateTime end);

    long countByEmployeeIdIsNull();
}
