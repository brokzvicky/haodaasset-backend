package com.vikkash.assetmanagementv1.config;

import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.entity.EmploymentStatus;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time idempotent migration that runs at every startup.
 *
 * The Employee Separation module added employment_status /
 * exit_clearance_status columns via Hibernate's ddl-auto=update. That adds
 * the columns but leaves them NULL on every row that already existed in the
 * database — Hibernate does not retroactively apply a Java field's default
 * value to existing rows. Left NULL, those employees would not show up as
 * "Active" anywhere (dashboard counts, status badges, filters), which is
 * wrong: every pre-existing employee who hasn't separated is Active by
 * definition.
 *
 * This back-fills employment_status='Active' and exit_clearance_status='Pending'
 * for any row where they are still NULL. Safe to run repeatedly — once every
 * row has a value, there is nothing left to update.
 */
@Component
@Order(1)
public class EmployeeEmploymentStatusMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEmploymentStatusMigration.class);

    private final EmployeeRepository employeeRepository;

    public EmployeeEmploymentStatusMigration(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Employee> all = employeeRepository.findAll();
        int fixed = 0;
        for (Employee employee : all) {
            boolean changed = false;
            if (employee.getEmploymentStatus() == null || employee.getEmploymentStatus().isBlank()) {
                employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
                changed = true;
            }
            if (employee.getExitClearanceStatus() == null || employee.getExitClearanceStatus().isBlank()) {
                employee.setExitClearanceStatus(EmploymentStatus.CLEARANCE_PENDING);
                changed = true;
            }
            if (changed) {
                employeeRepository.save(employee);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("EmployeeEmploymentStatusMigration: back-filled employment_status/exit_clearance_status on {} record(s).", fixed);
        } else {
            log.info("EmployeeEmploymentStatusMigration: all records already have an employment status, nothing to do.");
        }
    }
}
