package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.AttendanceRecordDTO;
import com.vikkash.assetmanagementv1.entity.AttendanceDevice;
import com.vikkash.assetmanagementv1.entity.AttendanceDeviceMapping;
import com.vikkash.assetmanagementv1.entity.AttendanceRecord;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.repository.AttendanceDeviceMappingRepository;
import com.vikkash.assetmanagementv1.repository.AttendanceDeviceRepository;
import com.vikkash.assetmanagementv1.repository.AttendanceRecordRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Speaks the eSSL / ZKTeco "ADMS" (Automatic Data Master Server) push
 * protocol — migrated in from the standalone eSSL Attendance POC, now
 * resolving punches against HaodaAsset's real {@link Employee} records
 * instead of a duplicate flat employee table.
 *
 * IMPORTANT for anyone new to this integration: despite the device's admin
 * menu saying "TCP Port 4370", ADMS mode itself is plain HTTP, not a raw
 * TCP socket protocol. Port 4370 is the port the *SDK-based* pull protocol
 * (UDP/TCP) uses when software queries the device directly. In ADMS mode
 * the device becomes the client: it periodically opens an HTTP connection
 * to whatever "Server Address" / "Server Port" you configured on the device
 * and calls a small, fixed set of URLs on our server:
 *
 *   GET  /iclock/cdata?SN=...               - handshake / registration ping
 *   POST /iclock/cdata?SN=...&table=ATTLOG  - pushes buffered punch logs
 *   GET  /iclock/getrequest?SN=...          - device polls for pending commands
 *
 * These paths and query params are fixed by the device firmware itself and
 * are NOT ours to rename — see {@link com.vikkash.assetmanagementv1.controller.AttendanceAdmsController}.
 */
@Service
public class AttendanceAdmsService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceAdmsService.class);

    private static final DateTimeFormatter DEVICE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Common ZKTeco/eSSL "status" codes for punch direction. These are
    // configurable per device model in the eSSL admin menu — if the
    // attendance list shows the wrong IN/OUT direction, this map is the
    // first place to adjust, or fall back to the alternating heuristic below.
    private static final Map<String, String> STATUS_TO_PUNCH_TYPE = new HashMap<>();
    static {
        STATUS_TO_PUNCH_TYPE.put("0", "IN");
        STATUS_TO_PUNCH_TYPE.put("1", "OUT");
        STATUS_TO_PUNCH_TYPE.put("4", "IN");  // Overtime In
        STATUS_TO_PUNCH_TYPE.put("5", "OUT"); // Overtime Out
    }

    // Common ZKTeco/eSSL "verify mode" codes — again, model-dependent.
    private static final Map<String, String> VERIFY_MODE_LABELS = new HashMap<>();
    static {
        VERIFY_MODE_LABELS.put("0", "Password");
        VERIFY_MODE_LABELS.put("1", "Fingerprint");
        VERIFY_MODE_LABELS.put("2", "Card");
        VERIFY_MODE_LABELS.put("15", "Face");
    }

    private final AttendanceDeviceRepository deviceRepository;
    private final AttendanceDeviceMappingRepository mappingRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceEventPublisher eventPublisher;

    public AttendanceAdmsService(AttendanceDeviceRepository deviceRepository,
                                  AttendanceDeviceMappingRepository mappingRepository,
                                  AttendanceRecordRepository attendanceRecordRepository,
                                  EmployeeRepository employeeRepository,
                                  AttendanceEventPublisher eventPublisher) {
        this.deviceRepository = deviceRepository;
        this.mappingRepository = mappingRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeRepository = employeeRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Handles GET /iclock/cdata (no ATTLOG table param) — the device's
     * handshake. We register/refresh the device row and reply with the
     * fixed config block ADMS devices expect (poll every ~10-30s, no
     * realtime photo upload, etc.).
     */
    @Transactional
    public String handleHandshake(String serialNumber, String pushVersion, String remoteIp) {
        AttendanceDevice device = findOrCreateDevice(serialNumber);
        device.setLastSeenAt(LocalDateTime.now());
        device.setLastIpAddress(remoteIp);
        if (pushVersion != null && !pushVersion.isBlank()) {
            device.setPushVersion(pushVersion);
        }
        deviceRepository.save(device);

        log.info("ADMS handshake from device SN={} ip={} pushVer={}", serialNumber, remoteIp, pushVersion);

        return "GET OPTION FROM: " + serialNumber + "\r\n"
                + "ATTLOGStamp=None\r\n"
                + "OPERLOGStamp=None\r\n"
                + "ErrorDelay=30\r\n"
                + "Delay=10\r\n"
                + "TransFlag=1111000000\r\n"
                + "Realtime=1\r\n"
                + "Encrypt=None\r\n";
    }

    /**
     * Handles GET /iclock/getrequest — the device asking "anything for me
     * to do?" (e.g. reboot, clear data, upload user list). No remote
     * commands are queued from this module, so we always answer "no
     * pending commands".
     */
    public String handleGetRequest(String serialNumber) {
        log.debug("ADMS getrequest poll from device SN={}", serialNumber);
        return "OK";
    }

    /**
     * Handles POST /iclock/cdata?table=ATTLOG — the actual punch data.
     * Body is plain text, one punch per line, tab-separated fields:
     *   PIN \t Time \t Status \t Verify \t WorkCode ...
     * Unknown/extra trailing fields are ignored — devices vary in how many
     * columns they send.
     */
    @Transactional
    public int handleAttendanceLogs(String serialNumber, String body, String remoteIp) {
        if (body == null || body.isBlank()) {
            log.warn("ADMS ATTLOG push from SN={} had an empty body - nothing to save", serialNumber);
            return 0;
        }

        AttendanceDevice device = findOrCreateDevice(serialNumber);
        device.setLastSeenAt(LocalDateTime.now());
        device.setLastIpAddress(remoteIp);
        deviceRepository.save(device);

        String[] lines = body.split("\r\n|\n|\r");
        int savedCount = 0;

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            try {
                AttendanceRecord saved = parseAndSaveLine(line.trim(), device);
                if (saved != null) {
                    savedCount++;
                    eventPublisher.publish(AttendanceRecordDTO.from(saved));
                }
            } catch (Exception ex) {
                // One malformed line should never take down the whole batch —
                // log it and keep processing the rest of the device's payload.
                log.error("Failed to parse ADMS ATTLOG line from SN={}: '{}' - {}", serialNumber, line, ex.getMessage());
            }
        }

        log.info("ADMS ATTLOG push from SN={}: {} line(s) received, {} saved", serialNumber, lines.length, savedCount);
        return savedCount;
    }

    private AttendanceDevice findOrCreateDevice(String serialNumber) {
        return deviceRepository.findBySerialNumber(serialNumber)
                .orElseGet(() -> {
                    AttendanceDevice newDevice = new AttendanceDevice();
                    newDevice.setSerialNumber(serialNumber);
                    newDevice.setDeviceName(serialNumber); // admin can rename later via the device mapping screen
                    newDevice.setFirstSeenAt(LocalDateTime.now());
                    return newDevice;
                });
    }

    private AttendanceRecord parseAndSaveLine(String line, AttendanceDevice device) {
        String[] fields = line.split("\t");
        if (fields.length < 2) {
            log.warn("Skipping ADMS line with too few fields (need at least PIN + Time): '{}'", line);
            return null;
        }

        String devicePin = fields[0].trim();
        LocalDateTime punchTime = LocalDateTime.parse(fields[1].trim(), DEVICE_TIME_FORMAT);
        String statusCode = fields.length > 2 ? fields[2].trim() : null;
        String verifyCode = fields.length > 3 ? fields[3].trim() : null;

        // De-duplicate: ADMS devices resend un-acknowledged logs, so the exact
        // same (device, pin, time) triple can legitimately arrive twice.
        Optional<AttendanceRecord> existing = attendanceRecordRepository
                .findByDeviceSerialNumberAndDeviceUserIdAndPunchTime(device.getSerialNumber(), devicePin, punchTime);
        if (existing.isPresent()) {
            log.debug("Duplicate punch ignored: SN={} pin={} time={}", device.getSerialNumber(), devicePin, punchTime);
            return null;
        }

        // Resolve devicePin -> HaodaAsset Employee via the mapping table.
        // Falls back to an "Unmapped" placeholder so the punch is never
        // silently dropped just because nobody has mapped the PIN yet.
        Optional<AttendanceDeviceMapping> mapping = mappingRepository.findByDevicePin(devicePin);
        Employee employee = mapping.flatMap(m -> employeeRepository.findByEmployeeId(m.getEmployeeId())).orElse(null);

        AttendanceRecord record = new AttendanceRecord();
        record.setDeviceUserId(devicePin);
        if (employee != null) {
            record.setEmployeeId(employee.getEmployeeId());
            record.setEmployeeName(employee.getEmployeeName());
            record.setDepartment(employee.getDepartment());
            record.setStatus("RECEIVED");
        } else {
            record.setEmployeeId(null);
            record.setEmployeeName("Unmapped (PIN " + devicePin + ")");
            record.setStatus("UNMAPPED");
        }
        record.setPunchTime(punchTime);
        record.setPunchType(resolvePunchType(statusCode, devicePin, punchTime));
        record.setVerifyMode(VERIFY_MODE_LABELS.getOrDefault(verifyCode, "Unknown"));
        record.setDeviceSerialNumber(device.getSerialNumber());
        record.setDeviceName(device.getDeviceName());
        record.setRawLine(line);
        record.setReceivedAt(LocalDateTime.now());

        return attendanceRecordRepository.save(record);
    }

    /**
     * Determines IN vs OUT. Prefers the device's own status code when it maps
     * to a known direction; otherwise falls back to a simple alternation
     * heuristic (this PIN's previous punch today decides the next one),
     * since some eSSL configurations send the same status code for every
     * punch and expect the server to infer direction.
     */
    private String resolvePunchType(String statusCode, String devicePin, LocalDateTime punchTime) {
        if (statusCode != null && STATUS_TO_PUNCH_TYPE.containsKey(statusCode)) {
            return STATUS_TO_PUNCH_TYPE.get(statusCode);
        }

        LocalDateTime startOfDay = punchTime.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        long punchesTodaySoFar = attendanceRecordRepository
                .countByDeviceUserIdAndPunchTimeBetween(devicePin, startOfDay, endOfDay);

        return (punchesTodaySoFar % 2 == 0) ? "IN" : "OUT";
    }
}
