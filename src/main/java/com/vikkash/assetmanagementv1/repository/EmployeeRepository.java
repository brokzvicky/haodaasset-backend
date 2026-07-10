package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmployeeId(String employeeId);
    boolean existsByEmployeeId(String employeeId);
    boolean existsByEmail(String email);

    /**
     * Used by the "Send Asset Email" search box: matches on Employee ID,
     * Employee Name, or Email (case-insensitive, partial match).
     */
    List<Employee> findByEmployeeIdContainingIgnoreCaseOrEmployeeNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String employeeId, String employeeName, String email);
}
