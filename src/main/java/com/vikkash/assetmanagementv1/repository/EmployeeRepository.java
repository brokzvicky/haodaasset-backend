package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /** Distinct department vocab for the AI Search intent parser (typo correction / validation). */
    @Query("SELECT DISTINCT e.department FROM Employee e WHERE e.department IS NOT NULL AND e.department <> ''")
    List<String> findDistinctDepartments();

    /** Resolves "assets in Finance" → the list of employeeIds in that department (partial, case-insensitive). */
    List<Employee> findByDepartmentContainingIgnoreCase(String department);
}
