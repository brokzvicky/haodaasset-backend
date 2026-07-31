package com.vikkash.assetmanagementv1.config;

import com.vikkash.assetmanagementv1.entity.Admin;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.entity.Permission;
import com.vikkash.assetmanagementv1.entity.Role;
import com.vikkash.assetmanagementv1.repository.AdminRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import com.vikkash.assetmanagementv1.repository.PermissionRepository;
import com.vikkash.assetmanagementv1.repository.RoleRepository;
import com.vikkash.assetmanagementv1.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds Roles/Permissions, a default admin account, and demo employees the
 * first time the application starts against an empty database. Also
 * backfills the new fine-grained Role assignment onto any pre-existing
 * admin row from before this migration, so upgrading an already-running
 * deployment doesn't strip existing admins of access.
 *
 * Safe to leave enabled for local/development use. For production, set
 * {@code app.seed.demo-data=false} to skip the demo admin/employee rows
 * (Roles/Permissions still seed either way — they're not "demo data", the
 * application depends on them existing).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminRepository      adminRepository;
    private final EmployeeRepository   employeeRepository;
    private final RoleRepository       roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder      passwordEncoder;

    @Value("${app.admin.recovery-email:}")
    private String adminRecoveryEmail;

    /**
     * Set a real password via this property (env var APP_SEED_ADMIN_PASSWORD)
     * before first boot in any shared/production environment. If left blank,
     * a random password is generated and printed to the log ONCE — there is
     * no hardcoded fallback like the old "admin123" default, precisely
     * because that default being public in source control is a real
     * security hole for any deployment that forgets to change it.
     */
    @Value("${app.seed.admin-password:}")
    private String configuredAdminPassword;

    /** Set false in production to skip seeding the demo admin/employee rows. Roles/Permissions always seed. */
    @Value("${app.seed.demo-data:true}")
    private boolean seedDemoData;

    public DataSeeder(AdminRepository adminRepository,
                      EmployeeRepository employeeRepository,
                      RoleRepository roleRepository,
                      PermissionRepository permissionRepository,
                      PasswordEncoder passwordEncoder) {
        this.adminRepository     = adminRepository;
        this.employeeRepository  = employeeRepository;
        this.roleRepository      = roleRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder     = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedPermissions();
        seedRoles();
        backfillRoleOnExistingAdmins();

        if (seedDemoData) {
            seedAdmin();
            seedEmployees();
        }
        backfillAdminEmail();
    }

    // ── Permissions & Roles ─────────────────────────────────────────────────

    /** code, label, module */
    private static final String[][] PERMISSION_DEFS = {
        {"DASHBOARD_VIEW",            "View dashboard",                 "Dashboard"},
        {"AI_SEARCH_USE",             "Use AI search & assistant",      "AI Search"},
        {"ASSETS_VIEW",               "View assets",                    "Assets"},
        {"ASSETS_WRITE",              "Add / edit / delete assets",     "Assets"},
        {"EMPLOYEES_VIEW",            "View employees",                 "Employees"},
        {"EMPLOYEES_WRITE",           "Add / edit employees, manage separation", "Employees"},
        {"ASSET_REQUESTS_VIEW",       "View asset requests",            "Asset Requests"},
        {"ASSET_REQUESTS_MANAGE",     "Approve / reject asset requests","Asset Requests"},
        {"NETWORK_CREDENTIALS_VIEW",  "View network credentials",       "Network Credentials"},
        {"NETWORK_CREDENTIALS_MANAGE","Add / edit / unlock credentials","Network Credentials"},
        {"SERVICE_BILLING_VIEW",      "View service billing",           "Service Billing"},
        {"SERVICE_BILLING_MANAGE",    "Add / edit service billing records","Service Billing"},
        {"MAINTENANCE_VIEW",          "View maintenance records",       "Maintenance"},
        {"MAINTENANCE_MANAGE",        "Add / edit maintenance records", "Maintenance"},
        {"PULSE_VIEW",                "View Haoda Pulse",               "Haoda Pulse"},
        {"FILE_CENTER_VIEW",          "View File Center",               "File Center"},
        {"FILE_CENTER_MANAGE",        "Upload / manage shared files",   "File Center"},
        {"REPORTS_VIEW",              "View reports",                   "Reports"},
        {"EMAIL_LOGS_VIEW",           "View email logs",                "Email Logs"},
        {"SEND_ASSET_EMAIL",          "Send asset emails",              "Send Asset Email"},
        {"ASSET_EMAIL_LOGS_VIEW",     "View asset email logs",          "Asset Email Logs"},
        {"SETTINGS_MANAGE",          "Manage system settings",         "Settings"},
        {"ACTIVITY_LOG_VIEW",         "View activity / audit log",      "Activity Log"},
        {"ATTENDANCE_VIEW",           "View attendance records & devices", "Attendance"},
        {"ATTENDANCE_MANAGE",         "Manage device-employee mappings & devices", "Attendance"},
    };

    private void seedPermissions() {
        for (String[] def : PERMISSION_DEFS) {
            if (!permissionRepository.existsByCode(def[0])) {
                permissionRepository.save(new Permission(def[0], def[1], def[2]));
            }
        }
    }

    private Set<Permission> permissionsByCode(String... codes) {
        Set<Permission> set = new LinkedHashSet<>();
        for (String code : codes) {
            permissionRepository.findByCode(code).ifPresent(set::add);
        }
        return set;
    }

    private Set<Permission> allPermissions() {
        return new LinkedHashSet<>(permissionRepository.findAll());
    }

    /**
     * Creates the default Role catalogue if it doesn't exist yet.
     * Deliberately does NOT touch the permission set of a Role that already
     * exists — once seeded, a Role's permissions are expected to be managed
     * going forward through a "Manage Roles" admin screen, and re-running
     * the seeder on every boot must never silently overwrite those edits.
     */
    private void seedRoles() {
        createRoleIfMissing("SYSTEM_ADMIN", "System Admin",
                "Full access to every module — the only role that can manage other Roles.",
                allPermissions());

        createRoleIfMissing("ASSET_MANAGER", "Asset Manager",
                "Owns the asset lifecycle: inventory, requests, maintenance, and reporting.",
                permissionsByCode("DASHBOARD_VIEW", "AI_SEARCH_USE", "ASSETS_VIEW", "ASSETS_WRITE",
                        "ASSET_REQUESTS_VIEW", "ASSET_REQUESTS_MANAGE", "MAINTENANCE_VIEW", "MAINTENANCE_MANAGE",
                        "NETWORK_CREDENTIALS_VIEW", "REPORTS_VIEW", "EMAIL_LOGS_VIEW"));

        createRoleIfMissing("HR_ADMIN", "HR Admin",
                "Owns employee records, onboarding/offboarding, workforce reporting, and attendance.",
                permissionsByCode("DASHBOARD_VIEW", "AI_SEARCH_USE", "EMPLOYEES_VIEW", "EMPLOYEES_WRITE",
                        "REPORTS_VIEW", "ACTIVITY_LOG_VIEW", "ATTENDANCE_VIEW", "ATTENDANCE_MANAGE"));

        createRoleIfMissing("SUPPORT_ENGINEER", "Support Engineer",
                "Front-line IT support: asset lookup, maintenance, network credential lookup, and attendance device health.",
                permissionsByCode("DASHBOARD_VIEW", "AI_SEARCH_USE", "ASSETS_VIEW", "MAINTENANCE_VIEW",
                        "MAINTENANCE_MANAGE", "NETWORK_CREDENTIALS_VIEW", "SERVICE_BILLING_VIEW", "ATTENDANCE_VIEW"));

        createRoleIfMissing("EMPLOYEE", "Employee",
                "Standard employee self-service role (My Assets, My Files, Asset Requests).",
                Set.of());
    }

    private void createRoleIfMissing(String name, String label, String description, Set<Permission> permissions) {
        if (roleRepository.existsByName(name)) return;
        Role role = new Role(name, label, description);
        role.setPermissions(permissions);
        roleRepository.save(role);
        log.info("Seeded role {} ({} permissions)", name, permissions.size());
    }

    /**
     * Any admin row created before this migration (or via direct DB insert)
     * has no roleRef yet. Under the new fine-grained model that means zero
     * permissions — which would silently hide every module for an existing
     * admin the moment the frontend switches to permission-driven rendering.
     * Backfill them onto SYSTEM_ADMIN (the safest default: it's exactly the
     * access every admin already implicitly had before this change).
     */
    private void backfillRoleOnExistingAdmins() {
        Role systemAdmin = roleRepository.findByName("SYSTEM_ADMIN").orElse(null);
        if (systemAdmin == null) return;

        List<Admin> toBackfill = adminRepository.findAll().stream()
                .filter(a -> a.getRoleRef() == null)
                .collect(Collectors.toList());

        for (Admin admin : toBackfill) {
            admin.setRoleRef(systemAdmin);
            adminRepository.save(admin);
        }
        if (!toBackfill.isEmpty()) {
            log.info("Backfilled SYSTEM_ADMIN role onto {} pre-existing admin account(s)", toBackfill.size());
        }
    }

    // ── Default admin + demo employees ──────────────────────────────────────

    private void seedAdmin() {
        if (adminRepository.existsByUsername("admin")) return;

        String passwordToUse = configuredAdminPassword;
        boolean generated = false;
        if (passwordToUse == null || passwordToUse.isBlank()) {
            passwordToUse = generateRandomPassword();
            generated = true;
        }

        Admin admin = new Admin();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(passwordToUse));
        admin.setName("System Administrator");
        roleRepository.findByName("SYSTEM_ADMIN").ifPresent(admin::setRoleRef);
        if (adminRecoveryEmail != null && !adminRecoveryEmail.isBlank()) {
            admin.setEmail(adminRecoveryEmail.trim().toLowerCase());
        }
        adminRepository.save(admin);

        if (generated) {
            log.warn("=================================================================");
            log.warn(" Seeded default admin account (username=admin)");
            log.warn(" GENERATED PASSWORD (shown only this once): {}", passwordToUse);
            log.warn(" Set app.seed.admin-password / APP_SEED_ADMIN_PASSWORD instead,");
            log.warn(" or change this password immediately after first login.");
            log.warn("=================================================================");
        } else {
            log.info("Seeded default admin account (username=admin) using configured app.seed.admin-password");
        }
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }

    /**
     * Backfills a recovery email onto existing admin accounts that don't
     * have one yet (e.g. deployments created before the Forgot Password
     * feature existed). No-op if ADMIN_RECOVERY_EMAIL isn't configured or
     * the admin already has an email on file.
     */
    private void backfillAdminEmail() {
        if (adminRecoveryEmail == null || adminRecoveryEmail.isBlank()) return;
        String normalized = adminRecoveryEmail.trim().toLowerCase();

        adminRepository.findByUsername("admin").ifPresent(admin -> {
            if (admin.getEmail() == null || admin.getEmail().isBlank()) {
                admin.setEmail(normalized);
                adminRepository.save(admin);
                log.info("Backfilled recovery email for admin account from ADMIN_RECOVERY_EMAIL");
            }
        });
    }

    private void seedEmployees() {
        if (employeeRepository.count() > 0) return;

        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElse(null);

        Object[][] demo = {
            {"EMP001","Priya Sharma",  "priya.sharma@company.com",   "Engineering",    "Software Engineer",  "Chennai, IN"},
            {"EMP002","Rahul Verma",   "rahul.verma@company.com",    "Infrastructure", "DevOps Engineer",    "Bengaluru, IN"},
            {"EMP003","Anjali Nair",   "anjali.nair@company.com",    "Design",         "UI/UX Designer",     "Mumbai, IN"},
            {"EMP004","Karthik Rajan", "karthik.rajan@company.com",  "Product",        "Product Manager",    "Chennai, IN"},
            {"EMP005","Divya Menon",   "divya.menon@company.com",    "Quality",        "QA Engineer",        "Hyderabad, IN"},
        };

        for (Object[] row : demo) {
            Employee e = new Employee();
            e.setEmployeeId((String) row[0]);
            e.setEmployeeName((String) row[1]);
            e.setEmail((String) row[2]);
            e.setDepartment((String) row[3]);
            e.setDesignation((String) row[4]);
            e.setLocation((String) row[5]);
            e.setRole("EMPLOYEE");
            e.setRoleRef(employeeRole);
            e.setPassword(passwordEncoder.encode(EmployeeService.DEFAULT_PASSWORD));
            e.setMustChangePassword(true);
            employeeRepository.save(e);
        }
        log.info("Seeded {} demo employees (password={})", demo.length, EmployeeService.DEFAULT_PASSWORD);
    }
}
