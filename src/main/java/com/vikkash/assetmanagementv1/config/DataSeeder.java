package com.vikkash.assetmanagementv1.config;

import com.vikkash.assetmanagementv1.entity.Admin;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.repository.AdminRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import com.vikkash.assetmanagementv1.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a default admin account and demo employees the first time the
 * application starts against an empty database.
 *
 * Safe to leave enabled for local/development use. For production set
 * a separate profile or guard on an environment variable instead.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AdminRepository    adminRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder    passwordEncoder;

    public DataSeeder(AdminRepository adminRepository,
                      EmployeeRepository employeeRepository,
                      PasswordEncoder passwordEncoder) {
        this.adminRepository    = adminRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder    = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin();
        seedEmployees();
    }

    private void seedAdmin() {
        if (adminRepository.existsByUsername("admin")) return;

        Admin admin = new Admin();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        adminRepository.save(admin);
        log.info("Seeded default admin account (username=admin)");
    }

    private void seedEmployees() {
        if (employeeRepository.count() > 0) return;

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
            e.setPassword(passwordEncoder.encode(EmployeeService.DEFAULT_PASSWORD));
            e.setMustChangePassword(true);
            employeeRepository.save(e);
        }
        log.info("Seeded {} demo employees (password={})", demo.length, EmployeeService.DEFAULT_PASSWORD);
    }
}
