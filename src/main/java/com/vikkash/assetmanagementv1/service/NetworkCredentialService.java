package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.NetworkCredentialCreateRequest;
import com.vikkash.assetmanagementv1.dto.NetworkCredentialResponse;
import com.vikkash.assetmanagementv1.dto.NetworkCredentialUpdateRequest;
import com.vikkash.assetmanagementv1.entity.NetworkCredential;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.NetworkCredentialRepository;
import com.vikkash.assetmanagementv1.security.CredentialEncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * All network-credential business logic lives here. Controllers only
 * handle HTTP concerns; this service owns encryption boundaries, audit
 * fields, and validation that can't be expressed as bean-validation
 * annotations alone.
 */
@Service
public class NetworkCredentialService {

    private static final Logger log = LoggerFactory.getLogger(NetworkCredentialService.class);

    /** Device-type buckets surfaced on the dashboard, per the brief. */
    private static final List<String> DASHBOARD_TYPES =
            List.of("Router", "Switch", "Firewall", "Access Point", "Server");

    private final NetworkCredentialRepository repository;
    private final CredentialEncryptionUtil encryptionUtil;
    private final AuditLogService auditLogService;

    public NetworkCredentialService(NetworkCredentialRepository repository,
                                     CredentialEncryptionUtil encryptionUtil,
                                     AuditLogService auditLogService) {
        this.repository = repository;
        this.encryptionUtil = encryptionUtil;
        this.auditLogService = auditLogService;
    }

    // ── Read ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NetworkCredentialResponse> getAll() {
        return repository.findAll().stream()
                .map(NetworkCredentialResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public NetworkCredentialResponse getById(Long id) {
        return NetworkCredentialResponse.from(getEntityById(id));
    }

    /** Sets the optional reminder dates that power NETWORK_CREDENTIAL_ROTATION / FIRMWARE_UPDATE_REMINDER notifications. */
    @Transactional
    public NetworkCredentialResponse updateReminderDates(Long id, java.time.LocalDate rotationDueDate, java.time.LocalDate firmwareDueDate) {
        NetworkCredential entity = getEntityById(id);
        entity.setRotationDueDate(rotationDueDate);
        entity.setFirmwareDueDate(firmwareDueDate);
        return NetworkCredentialResponse.from(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<NetworkCredentialResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return getAll();
        }
        return repository.search(query.trim()).stream()
                .map(NetworkCredentialResponse::from)
                .collect(Collectors.toList());
    }

    /** Internal helper — fetches the real entity (with ciphertext) for service-internal use only. Never return this directly to a controller. */
    @Transactional(readOnly = true)
    public NetworkCredential getEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Network credential not found with id: " + id));
    }

    // ── Dashboard ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardStats() {
        Map<String, Long> typeCounts = DASHBOARD_TYPES.stream()
                .collect(Collectors.toMap(
                        type -> "total" + type.replace(" ", ""),
                        repository::countByDeviceType
                ));

        return Map.of(
                "totalDevices", repository.count(),
                "byType", typeCounts,
                "recentlyAdded", repository.findTop5ByOrderByCreatedAtDesc().stream()
                        .map(NetworkCredentialResponse::from).collect(Collectors.toList()),
                "recentlyUpdated", repository.findTop5ByOrderByUpdatedAtDesc().stream()
                        .map(NetworkCredentialResponse::from).collect(Collectors.toList())
        );
    }

    // ── Create ─────────────────────────────────────────────────────────────

    @Transactional
    public NetworkCredentialResponse create(NetworkCredentialCreateRequest request, String createdBy) {
        NetworkCredential entity = new NetworkCredential();
        applyCommonFields(entity, request.getDeviceName(), request.getDeviceType(), request.getBrand(),
                request.getModel(), request.getIpAddress(), request.getHostname(), request.getUsername(),
                request.getSshPort(), request.getWebPort(), request.getLocation(), request.getVlan(),
                request.getIsp(), request.getNotes(), request.getDeviceStatus());

        entity.setEncryptedPassword(encryptionUtil.encrypt(request.getPassword()));
        if (request.getEnablePassword() != null && !request.getEnablePassword().isBlank()) {
            entity.setEncryptedEnablePassword(encryptionUtil.encrypt(request.getEnablePassword()));
        }

        entity.setCreatedBy(createdBy);
        entity.setUpdatedBy(createdBy);

        log.info("Creating network credential: device={} type={} createdBy={}",
                entity.getDeviceName(), entity.getDeviceType(), createdBy);

        NetworkCredential saved = repository.save(entity);
        auditLogService.record("NETWORK_CREDENTIAL", String.valueOf(saved.getId()), "CREATED",
                "Added device '" + saved.getDeviceName() + "' (" + saved.getDeviceType() + ")", createdBy);
        return NetworkCredentialResponse.from(saved);
    }

    // ── Update ─────────────────────────────────────────────────────────────

    @Transactional
    public NetworkCredentialResponse update(Long id, NetworkCredentialUpdateRequest request, String updatedBy) {
        NetworkCredential entity = getEntityById(id);

        applyCommonFields(entity, request.getDeviceName(), request.getDeviceType(), request.getBrand(),
                request.getModel(), request.getIpAddress(), request.getHostname(), request.getUsername(),
                request.getSshPort(), request.getWebPort(), request.getLocation(), request.getVlan(),
                request.getIsp(), request.getNotes(), request.getDeviceStatus());

        // Passwords are optional on update — only re-encrypt if a new value was actually provided.
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            entity.setEncryptedPassword(encryptionUtil.encrypt(request.getPassword()));
        }
        if (request.getEnablePassword() != null && !request.getEnablePassword().isBlank()) {
            entity.setEncryptedEnablePassword(encryptionUtil.encrypt(request.getEnablePassword()));
        }

        entity.setUpdatedBy(updatedBy);

        log.info("Updated network credential id={} device={} updatedBy={}", id, entity.getDeviceName(), updatedBy);

        NetworkCredential saved = repository.save(entity);
        auditLogService.record("NETWORK_CREDENTIAL", String.valueOf(saved.getId()), "UPDATED",
                "Updated device '" + saved.getDeviceName() + "'", updatedBy);
        return NetworkCredentialResponse.from(saved);
    }

    private void applyCommonFields(NetworkCredential entity, String deviceName, String deviceType, String brand,
                                    String model, String ipAddress, String hostname, String username,
                                    Integer sshPort, Integer webPort, String location, String vlan,
                                    String isp, String notes, String deviceStatus) {
        entity.setDeviceName(deviceName);
        entity.setDeviceType(deviceType);
        entity.setBrand(brand);
        entity.setModel(model);
        entity.setIpAddress(ipAddress);
        entity.setHostname(hostname);
        entity.setUsername(username);
        entity.setSshPort(sshPort);
        entity.setWebPort(webPort);
        entity.setLocation(location);
        entity.setVlan(vlan);
        entity.setIsp(isp);
        entity.setNotes(notes);
        if (deviceStatus != null && !deviceStatus.isBlank()) {
            entity.setDeviceStatus(deviceStatus);
        }
    }

    // ── Reveal (decrypt on demand) ──────────────────────────────────────────

    /**
     * Decrypts and returns the device login password for one record.
     * Called only when an admin explicitly clicks "Show Password" for that
     * specific row — never as part of a list/get response.
     */
    @Transactional(readOnly = true)
    public String revealPassword(Long id, String requestedBy) {
        NetworkCredential entity = getEntityById(id);
        log.info("Password revealed for network credential id={} device={} by={}",
                id, entity.getDeviceName(), requestedBy);
        auditLogService.record("NETWORK_CREDENTIAL", String.valueOf(id), "PASSWORD_REVEALED",
                "Login password revealed for device '" + entity.getDeviceName() + "'", requestedBy);
        return encryptionUtil.decrypt(entity.getEncryptedPassword());
    }

    @Transactional(readOnly = true)
    public String revealEnablePassword(Long id, String requestedBy) {
        NetworkCredential entity = getEntityById(id);
        if (entity.getEncryptedEnablePassword() == null) {
            return null;
        }
        log.info("Enable password revealed for network credential id={} device={} by={}",
                id, entity.getDeviceName(), requestedBy);
        auditLogService.record("NETWORK_CREDENTIAL", String.valueOf(id), "PASSWORD_REVEALED",
                "Enable password revealed for device '" + entity.getDeviceName() + "'", requestedBy);
        return encryptionUtil.decrypt(entity.getEncryptedEnablePassword());
    }

    // ── Delete ─────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        NetworkCredential entity = getEntityById(id);
        log.warn("Deleting network credential id={}", id);
        repository.deleteById(id);
        auditLogService.record("NETWORK_CREDENTIAL", String.valueOf(id), "DELETED",
                "Deleted device '" + entity.getDeviceName() + "'");
    }
}
