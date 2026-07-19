package com.vikkash.assetmanagementv1.dto;

/**
 * The ONLY thing the natural-language intent parser is allowed to produce.
 * Every field here is a whitelisted, validated attribute — there is no path
 * from user text to raw SQL. AiSearchService turns this into a
 * Specification/CriteriaBuilder query; unknown/unmatched terms are simply
 * dropped (see AiIntentParserService's `ignoredTerms`), never passed through.
 */
public class AiSearchFilters {

    private String category;        // Asset.assetType (validated against live vocab)
    private String brand;           // Asset.brand (validated against live vocab)
    private String department;      // Employee.department -> resolved to a set of employeeIds
    private String employeeName;    // Asset.employeeName (partial, validated/typo-corrected)
    private String branch;          // Asset.location (partial match)
    private String status;          // Asset.assetStatus (validated against live vocab)
    private String warrantyStatus;  // "Expired" | "ExpiringSoon" | "Active"
    private Integer purchaseYear;   // extracted from Asset.purchaseDate (yyyy-MM-dd)
    private String ram;             // Asset.ram (partial match, e.g. "16")
    private Boolean unassigned;     // true → employeeId is null/blank
    private String keyword;         // fallback loose match, only used when nothing structured matched

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getWarrantyStatus() { return warrantyStatus; }
    public void setWarrantyStatus(String warrantyStatus) { this.warrantyStatus = warrantyStatus; }

    public Integer getPurchaseYear() { return purchaseYear; }
    public void setPurchaseYear(Integer purchaseYear) { this.purchaseYear = purchaseYear; }

    public String getRam() { return ram; }
    public void setRam(String ram) { this.ram = ram; }

    public Boolean getUnassigned() { return unassigned; }
    public void setUnassigned(Boolean unassigned) { this.unassigned = unassigned; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public boolean isEmpty() {
        return category == null && brand == null && department == null && employeeName == null
                && branch == null && status == null && warrantyStatus == null && purchaseYear == null
                && ram == null && unassigned == null && keyword == null;
    }
}
