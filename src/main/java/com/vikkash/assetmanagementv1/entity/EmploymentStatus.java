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

    public static final String ACTIVE          = "Active";
    public static final String NOTICE_PERIOD   = "Notice Period";
    public static final String EXIT_CLEARANCE  = "Exit Clearance";
    public static final String ASSETS_RETURNED = "Assets Returned";
    public static final String RESIGNED        = "Resigned";

    /** Ordered so index comparisons can express "must not go backwards" if ever needed. */
    public static final List<String> ORDER = List.of(
            ACTIVE, NOTICE_PERIOD, EXIT_CLEARANCE, ASSETS_RETURNED, RESIGNED);

    public static final String CLEARANCE_PENDING   = "Pending";
    public static final String CLEARANCE_COMPLETED = "Completed";

    /** Standard resignation reason vocabulary shown in the dropdown. */
    public static final List<String> RESIGNATION_REASONS = List.of(
            "Better Opportunity",
            "Higher Studies",
            "Relocation",
            "Personal Reasons",
            "Health Reasons",
            "Retirement",
            "Termination",
            "End of Contract",
            "Other"
    );

    private EmploymentStatus() {
    }
}
