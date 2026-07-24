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

    /** Used by Google Sign-In: find the account this Google identity is linked to. */
    Optional<Employee> findByEmail(String email);

    /** Used by the "Login with Mobile" flow. */
    Optional<Employee> findByMobile(String mobile);

    /** Used by Google Sign-In to find an account already linked to this Google identity. */
    Optional<Employee> findByGoogleId(String googleId);

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

    // ── Separation / employment-lifecycle queries ───────────────────────────

    List<Employee> findByEmploymentStatus(String employmentStatus);

    List<Employee> findByEmploymentStatusIn(List<String> employmentStatuses);

    long countByEmploymentStatus(String employmentStatus);

    long countByEmploymentStatusIn(List<String> employmentStatuses);

    /** Exit clearance still outstanding: anyone past Active but not yet Resigned. */
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.employmentStatus NOT IN ('Active', 'Resigned')")
    long countPendingExitClearance();

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.employmentStatus = 'Resigned' " +
           "AND e.resignedDate >= :monthStart AND e.resignedDate <= :monthEnd")
    long countResignedBetween(String monthStart, String monthEnd);

    /** Employees currently in any separation stage (used by the Employee Exit Report). */
    @Query("SELECT e FROM Employee e WHERE e.employmentStatus <> 'Active' ORDER BY e.noticeStartDate DESC NULLS LAST")
    List<Employee> findAllInSeparation();

    // ── Lifecycle dashboard (Active / On Leave / Notice Period / Resigned / Terminated) ──────

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.joiningDate >= :monthStart AND e.joiningDate <= :monthEnd")
    long countJoinedBetween(String monthStart, String monthEnd);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.employmentStatus IN ('Resigned','Terminated') " +
           "AND ((e.resignedDate >= :monthStart AND e.resignedDate <= :monthEnd) " +
           "OR (e.terminationDate >= :monthStart AND e.terminationDate <= :monthEnd))")
    long countLeftBetween(String monthStart, String monthEnd);

    /** Backs the dedicated Resigned Employees view — newest resignation first. */
    @Query("SELECT e FROM Employee e WHERE e.employmentStatus = 'Resigned' ORDER BY e.resignedDate DESC NULLS LAST")
    List<Employee> findAllResigned();

    @Query("SELECT e FROM Employee e WHERE e.employmentStatus = 'Terminated' ORDER BY e.terminationDate DESC NULLS LAST")
    List<Employee> findAllTerminated();
}
