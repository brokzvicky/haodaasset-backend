package com.vikkash.assetmanagementv1.dto;

import java.util.List;

/**
 * Live, DB-derived vocab (brands, categories, departments, branches,
 * statuses) shown as "Smart Suggestions" while the AI Search box is
 * focused. Role-scoped: employees don't get the org's full department/brand
 * list, only what's relevant/visible to them.
 */
public class AiSearchFacetsDTO {
    private List<String> brands;
    private List<String> categories;
    private List<String> departments;
    private List<String> branches;
    private List<String> statuses;
    private List<String> employeeNames;

    public List<String> getBrands() { return brands; }
    public void setBrands(List<String> brands) { this.brands = brands; }

    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }

    public List<String> getDepartments() { return departments; }
    public void setDepartments(List<String> departments) { this.departments = departments; }

    public List<String> getBranches() { return branches; }
    public void setBranches(List<String> branches) { this.branches = branches; }

    public List<String> getStatuses() { return statuses; }
    public void setStatuses(List<String> statuses) { this.statuses = statuses; }

    public List<String> getEmployeeNames() { return employeeNames; }
    public void setEmployeeNames(List<String> employeeNames) { this.employeeNames = employeeNames; }
}
