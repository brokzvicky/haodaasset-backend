package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.service.AttendanceAdmsService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Device-facing endpoints for the Attendance Management module. These
 * paths and query params (/iclock/cdata, /iclock/getrequest, SN=,
 * table=ATTLOG) are fixed by the eSSL/ZKTeco ADMS protocol itself — they
 * are NOT ours to rename, since the biometric device's firmware calls
 * them exactly as written here. Point the device's "Server Address" at
 * this backend's host and "Server Port" at wherever it's exposed (e.g.
 * haodaasset-backend-1.onrender.com on 443, or the local dev port).
 *
 * No auth guard on purpose (see SecurityConfig — /iclock/** is permitAll):
 * the device can't do a login handshake, and in production this would
 * instead be protected by network-level restrictions (e.g. the device
 * only reaching us over a VPN/firewall allow-list) rather than an API key
 * the firmware doesn't support sending.
 */
@RestController
@RequestMapping("/iclock")
public class AttendanceAdmsController {

    private static final Logger log = LoggerFactory.getLogger(AttendanceAdmsController.class);

    private final AttendanceAdmsService admsService;

    public AttendanceAdmsController(AttendanceAdmsService admsService) {
        this.admsService = admsService;
    }

    /**
     * Device handshake. Called with no body when the device is just
     * checking in, or immediately before it starts POSTing attendance data.
     */
    @GetMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handshake(@RequestParam("SN") String serialNumber,
                                             @RequestParam(value = "pushver", required = false) String pushVersion,
                                             HttpServletRequest request) {
        try {
            String response = admsService.handleHandshake(serialNumber, pushVersion, request.getRemoteAddr());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("ADMS handshake failed for SN={}: {}", serialNumber, ex.getMessage(), ex);
            // Devices only understand plain "OK"/text bodies — a JSON error body would just confuse the firmware.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR");
        }
    }

    /**
     * Device pushing buffered attendance logs. table=ATTLOG is the only
     * table this module handles (OPERLOG/user-info sync tables are out of
     * scope, same as the original POC).
     */
    @PostMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> pushData(@RequestParam("SN") String serialNumber,
                                            @RequestParam(value = "table", required = false) String table,
                                            @RequestBody(required = false) String body,
                                            HttpServletRequest request) {
        try {
            if (table != null && !table.equalsIgnoreCase("ATTLOG")) {
                log.info("Ignoring ADMS push for unsupported table '{}' from SN={} (only ATTLOG is handled)", table, serialNumber);
                return ResponseEntity.ok("OK");
            }
            admsService.handleAttendanceLogs(serialNumber, body, request.getRemoteAddr());
            return ResponseEntity.ok("OK");
        } catch (Exception ex) {
            log.error("ADMS ATTLOG push failed for SN={}: {}", serialNumber, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR");
        }
    }

    /** Device polling for pending remote commands. None are ever queued, so this always answers "OK". */
    @GetMapping(value = "/getrequest", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getRequest(@RequestParam("SN") String serialNumber) {
        try {
            return ResponseEntity.ok(admsService.handleGetRequest(serialNumber));
        } catch (Exception ex) {
            log.error("ADMS getrequest failed for SN={}: {}", serialNumber, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR");
        }
    }
}
