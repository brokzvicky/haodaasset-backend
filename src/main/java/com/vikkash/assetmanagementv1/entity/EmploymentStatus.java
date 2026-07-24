package com.vikkash.assetmanagementv1.entity;

import java.util.List;

/**
 * The five stages of the Employee Separation / Resignation workflow.
 *
 *   Active -> Notice Period -> Exit Clearance -> Assets Returned -> Resigned
 *
 * Kept as plain String constants (rather than a JPA @Enumerated enum) to
 * match the rest of this codebase's convention of storing status fields as
 * free-form Strings (see Asset.assetStatus) — simpler schema evolution and
 * consistent with how the frontend already treats every other status field.
 */
public final class EmploymentStatus {

    // ── Top-level employee lifecycle statuses ───────────────────────────────
    // The full set of values `Employee.employmentStatus` can hold. Matches
    // the enterprise lifecycle spec: Active, On Leave, Notice Period,
    // Resigned, Terminated.
    public static final String ACTIVE          = "Active";
    public static final String ON_LEAVE        = "On Leave";
    public static final String NOTICE_PERIOD   = "Notice Period";
    public static final String RESIGNED        = "Resigned";
    public static final String TERMINATED      = "Terminated";

    /** Legacy sub-stages of Notice Period, retained only so old rows / audit history still read correctly. */
    public static final String EXIT_CLEARANCE  = "Exit Clearance";
    public static final String ASSETS_RETURNED = "Assets Returned";

    /** The 5 statuses the Employees page filter tabs (and the DB column) recognize. */
    public static final List<String> ALL_STATUSES = List.of(
            ACTIVE, ON_LEAVE, NOTICE_PERIOD, RESIGNED, TERMINATED);

    /** Any of these legacy/granular values are treated as "Notice Period" everywhere a top-level status is needed. */
    public static final List<String> NOTICE_PERIOD_BUCKET = List.of(
            NOTICE_PERIOD, EXIT_CLEARANCE, ASSETS_RETURNED);

    public static final List<String> ORDER = List.of(
            ACTIVE, NOTICE_PERIOD, EXIT_CLEARANCE, ASSETS_RETURNED, RESIGNED);

    public static final String CLEARANCE_PENDING   = "Pending";
    public static final String CLEARANCE_COMPLETED = "Completed";

    /** Standard exit-reason vocabulary shown in the dropdown for Resigned/Terminated. */
    public static final List<String> RESIGNATION_REASONS = List.of(
            "Better Opportunity",
            "Higher Studies",
            "Relocation",
            "Personal Reasons",
            "Health Reasons",
            "Retirement",
            "Termination",
            "Policy Violation",
            "Performance",
            "End of Contract",
            "Other"
    );

    /** Reasons shown when placing an employee On Leave. */
    public static final List<String> LEAVE_REASONS = List.of(
            "Medical Leave",
            "Maternity/Paternity Leave",
            "Sabbatical",
            "Personal Leave",
            "Bereavement Leave",
            "Other"
    );

    private EmploymentStatus() {
    }
}
