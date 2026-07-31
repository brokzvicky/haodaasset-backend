package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.AttendanceMappingRequest;
import com.vikkash.assetmanagementv1.dto.AttendanceMappingResponse;
import com.vikkash.assetmanagementv1.dto.AttendanceRecordDTO;
import com.vikkash.assetmanagementv1.dto.AttendanceStatsDTO;
import com.vikkash.assetmanagementv1.entity.AttendanceDevice;
import com.vikkash.assetmanagementv1.entity.AttendanceDeviceMapping;
import com.vikkash.assetmanagementv1.entity.AttendanceRecord;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AttendanceDeviceMappingRepository;
import com.vikkash.assetmanagementv1.repository.AttendanceDeviceRepository;
import com.vikkash.assetmanagementv1.repository.AttendanceRecordRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Attendance Management module: everything the admin-facing screens need —
 * the punch list/history, today's KPI stats, the registered device list,
 * and the device-PIN-to-employee mapping CRUD that resolves raw punches
 * into real HaodaAsset employees. Device ingestion itself (the ADMS push
 * protocol) lives in {@link AttendanceAdmsService}.
 */
@Service
public class AttendanceService {

    private final AttendanceRecordRepository recordRepository;
    private final AttendanceDeviceRepository deviceRepository;
    private final AttendanceDeviceMappingRepository mappingRepository;
    private final EmployeeRepository employeeRepository;

    public AttendanceService(AttendanceRecordRepository recordRepository,
                              AttendanceDeviceRepository deviceRepository,
                              AttendanceDeviceMappingRepository mappingRepository,
                              EmployeeRepository employeeRepository) {
        this.recordRepository = recordRepository;
        this.deviceRepository = deviceRepository;
        this.mappingRepository = mappingRepository;
        this.employeeRepository = employeeRepository;
    }

    // ── Records ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttendanceRecordDTO> getRecent() {
        return recordRepository.findTop200ByOrderByPunchTimeDesc().stream()
                .map(AttendanceRecordDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordDTO> getAll() {
        return recordRepository.findAllByOrderByPunchTimeDesc().stream()
                .map(AttendanceRecordDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordDTO> getForEmployee(String employeeId) {
        return recordRepository.findByEmployeeIdOrderByPunchTimeDesc(employeeId).stream()
                .map(AttendanceRecordDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordDTO> getForDateRange(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return recordRepository.findByPunchTimeBetweenOrderByPunchTimeDesc(start, end).stream()
                .map(AttendanceRecordDTO::from)
                .collect(Collectors.toList());
    }

    // ── Stats ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AttendanceStatsDTO getStats() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfTomorrow = startOfToday.plusDays(1);

        AttendanceStatsDTO stats = new AttendanceStatsDTO();
        stats.setTotalPunchesToday(recordRepository.countByPunchTimeBetween(startOfToday, startOfTomorrow));
        stats.setInPunchesToday(recordRepository.countByPunchTypeAndPunchTimeBetween("IN", startOfToday, startOfTomorrow));
        stats.setOutPunchesToday(recordRepository.countByPunchTypeAndPunchTimeBetween("OUT", startOfToday, startOfTomorrow));
        stats.setUnmappedPunchesToday(recordRepository.countByEmployeeIdIsNullAndPunchTimeBetween(startOfToday, startOfTomorrow));

        long distinctEmployeesToday = recordRepository.findByPunchTimeBetweenOrderByPunchTimeDesc(startOfToday, startOfTomorrow)
                .stream()
                .map(AttendanceRecord::getEmployeeId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        stats.setEmployeesPresentToday(distinctEmployeesToday);

        List<AttendanceDevice> devices = deviceRepository.findAll();
        stats.setDevicesTotal(devices.size());
        LocalDateTime onlineCutoff = LocalDateTime.now().minusMinutes(15);
        stats.setDevicesOnline(devices.stream()
                .filter(d -> d.getLastSeenAt() != null && d.getLastSeenAt().isAfter(onlineCutoff))
                .count());

        stats.setUnmappedDevicePins(recordRepository.countByEmployeeIdIsNull());

        return stats;
    }

    // ── Devices ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttendanceDevice> getDevices() {
        return deviceRepository.findAllByOrderByLastSeenAtDesc();
    }

    @Transactional
    public AttendanceDevice renameDevice(Long id, String deviceName, String location) {
        AttendanceDevice device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance device not found with id: " + id));
        if (deviceName != null && !deviceName.isBlank()) {
            device.setDeviceName(deviceName);
        }
        if (location != null) {
            device.setLocation(location);
        }
        return deviceRepository.save(device);
    }

    // ── Device PIN -> Employee mappings ─────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttendanceMappingResponse> getMappings() {
        return mappingRepository.findAllByOrderByDevicePinAsc().stream()
                .map(this::toMappingResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AttendanceMappingResponse createMapping(AttendanceMappingRequest req) {
        if (mappingRepository.existsByDevicePin(req.getDevicePin())) {
            throw new IllegalArgumentException("Device PIN " + req.getDevicePin() + " is already mapped to an employee.");
        }
        if (!employeeRepository.existsByEmployeeId(req.getEmployeeId())) {
            throw new ResourceNotFoundException("Employee not found with employeeId: " + req.getEmployeeId());
        }
        AttendanceDeviceMapping mapping = new AttendanceDeviceMapping();
        mapping.setDevicePin(req.getDevicePin().trim());
        mapping.setEmployeeId(req.getEmployeeId().trim());
        mapping = mappingRepository.save(mapping);
        return toMappingResponse(mapping);
    }

    @Transactional
    public AttendanceMappingResponse updateMapping(Long id, AttendanceMappingRequest req) {
        AttendanceDeviceMapping mapping = mappingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mapping not found with id: " + id));
        if (!employeeRepository.existsByEmployeeId(req.getEmployeeId())) {
            throw new ResourceNotFoundException("Employee not found with employeeId: " + req.getEmployeeId());
        }
        mapping.setEmployeeId(req.getEmployeeId().trim());
        mapping = mappingRepository.save(mapping);
        return toMappingResponse(mapping);
    }

    @Transactional
    public void deleteMapping(Long id) {
        if (!mappingRepository.existsById(id)) {
            throw new ResourceNotFoundException("Mapping not found with id: " + id);
        }
        mappingRepository.deleteById(id);
    }

    private AttendanceMappingResponse toMappingResponse(AttendanceDeviceMapping mapping) {
        Employee employee = employeeRepository.findByEmployeeId(mapping.getEmployeeId()).orElse(null);
        return new AttendanceMappingResponse(
                mapping.getId(),
                mapping.getDevicePin(),
                mapping.getEmployeeId(),
                employee != null ? employee.getEmployeeName() : "(employee not found)",
                employee != null ? employee.getDepartment() : null,
                employee != null
        );
    }
}
