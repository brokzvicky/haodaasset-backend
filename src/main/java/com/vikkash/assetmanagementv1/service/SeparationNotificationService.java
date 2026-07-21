package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.entity.SystemNotification;
import com.vikkash.assetmanagementv1.repository.SystemNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raises the notifications required by the Employee Separation workflow:
 *   - HR is notified when a separation starts (notice period begins)
 *   - IT is notified when assets must be collected (exit clearance begins)
 *   - Admin is notified when clearance is fully complete
 *
 * This application's notification center (the admin bell, backed by
 * {@link SystemNotification}) is a single shared inbox rather than
 * separate per-role mailboxes, so each notification's title is prefixed
 * with the intended audience (HR / IT / Admin) to make the routing
 * explicit to whoever is triaging the bell. If/when role-specific
 * notification routing is added, this is the single place that would
 * change to target it.
 */
@Service
public class SeparationNotificationService {

    private final SystemNotificationRepository notificationRepository;

    public SeparationNotificationService(SystemNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notifySeparationStarted(Employee employee) {
        save("SEPARATION_STARTED", "warning",
                "HR: Resignation process started — " + employee.getEmployeeName(),
                employee.getEmployeeName() + " (" + employee.getEmployeeId() + ") has entered the Notice Period. "
                        + "Last working date: " + safe(employee.getLastWorkingDate()) + ".",
                employee.getEmployeeId());
    }

    @Transactional
    public void notifyAssetCollectionRequired(Employee employee, int pendingAssetCount) {
        save("SEPARATION_ASSET_COLLECTION", "warning",
                "IT: Collect assets before exit — " + employee.getEmployeeName(),
                employee.getEmployeeName() + " (" + employee.getEmployeeId() + ") has moved to Exit Clearance with "
                        + pendingAssetCount + " asset(s) still assigned. Please arrange collection before "
                        + safe(employee.getLastWorkingDate()) + ".",
                employee.getEmployeeId());
    }

    @Transactional
    public void notifyClearanceComplete(Employee employee) {
        save("SEPARATION_CLEARANCE_COMPLETE", "info",
                "Admin: Exit clearance complete — " + employee.getEmployeeName(),
                employee.getEmployeeName() + " (" + employee.getEmployeeId() + ") has completed exit clearance and "
                        + "all assets have been returned. Ready to finalize as Resigned.",
                employee.getEmployeeId());
    }

    @Transactional
    public void notifyResignationFinalized(Employee employee) {
        save("SEPARATION_FINALIZED", "info",
                "Admin: Separation finalized — " + employee.getEmployeeName(),
                employee.getEmployeeName() + " (" + employee.getEmployeeId() + ") is now marked Resigned as of "
                        + safe(employee.getResignedDate()) + ". Employment history has been preserved.",
                employee.getEmployeeId());
    }

    private void save(String type, String severity, String title, String message, String relatedId) {
        SystemNotification notif = new SystemNotification();
        notif.setType(type);
        notif.setSeverity(severity);
        notif.setTitle(title);
        notif.setMessage(message);
        notif.setRelatedType("EMPLOYEE");
        notif.setRelatedId(relatedId);
        notificationRepository.save(notif);
    }

    private static String safe(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
