package com.vikkash.assetmanagementv1.service.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declares every function the AI assistant is allowed to call, in the shape
 * OpenAI's Responses API expects under the "tools" array. The model decides
 * on its own which of these to invoke based on the user's message — nothing
 * here is hardcoded per-phrase; GPT reads the name/description/parameters and
 * reasons about intent itself (synonyms, rephrasing, multi-step chains, etc.
 * all fall out of that, not from pattern matching).
 *
 * Employee-role callers only ever see a read-only subset (see
 * {@link #definitions(boolean)}) — admin-only tools are simply not offered
 * to the model when the caller isn't an admin, which is a stronger guarantee
 * than trusting the model to refuse.
 */
public final class AiToolSchema {

    private AiToolSchema() {}

    public static List<Map<String, Object>> definitions(boolean isAdmin) {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(tool("search_assets",
                "Search/filter/list IT assets (laptops, desktops, monitors, etc). Use for requests like " +
                "'show available laptops', 'search Dell laptops purchased this year', 'find unused assets', " +
                "'show warranty expiring this month', 'show my assets'. Always prefer this over guessing.",
                params(
                        prop("keyword", "string", "Free-text keyword: brand, model, employee name, serial number, location, etc. Omit if not applicable."),
                        prop("assetType", "string", "e.g. Laptop, Desktop, Monitor, Printer"),
                        prop("assetStatus", "string", "Available, Assigned, Retired, etc."),
                        prop("assetCondition", "string", "New, Good, Fair, Damaged"),
                        prop("onlyUnused", "boolean", "true = only assets that are Available and have never been assigned (\"unused assets\")"),
                        prop("purchasedThisYear", "boolean", "true = filter to assets purchased in the current calendar year"),
                        prop("warrantyExpiringThisMonth", "boolean", "true = filter to assets whose warranty expires this calendar month"),
                        prop("duplicateSerialsOnly", "boolean", "true = only return assets that share a serial number with another asset (data-quality check)")
                ), List.of()));

        tools.add(tool("get_asset_details",
                "Look up ONE specific asset by its ID, serial number, or asset/laptop name — e.g. 'who owns LAP-1023', " +
                "'where is asset 102', 'details on Dell Latitude 5440 assigned to John'.",
                params(prop("identifier", "string", "Asset ID, serial number, or name/model fragment")),
                List.of("identifier")));

        if (isAdmin) {
            tools.add(tool("create_asset",
                    "Add a brand-new asset to inventory. Ask the user for any required field you don't have yet " +
                    "(laptopName/model name and brand at minimum) before calling this.",
                    params(
                            prop("laptopName", "string", "Display name, e.g. 'Dell Latitude 5440'"),
                            prop("brand", "string", null),
                            prop("model", "string", null),
                            prop("assetType", "string", "Laptop, Desktop, Monitor, etc."),
                            prop("serialNumber", "string", null),
                            prop("vendor", "string", null),
                            prop("assetCost", "string", null),
                            prop("purchaseDate", "string", "YYYY-MM-DD"),
                            prop("warrantyExpiry", "string", "YYYY-MM-DD"),
                            prop("location", "string", null),
                            prop("processor", "string", null),
                            prop("ram", "string", null),
                            prop("storage", "string", null)
                    ), List.of("laptopName", "brand")));

            tools.add(tool("update_asset",
                    "Change one or more fields on an EXISTING asset. Look the asset up first with get_asset_details " +
                    "if you only have a name/serial, to get its numeric assetId.",
                    params(
                            prop("assetId", "integer", "Numeric asset ID"),
                            prop("fields", "object", "Map of field name -> new value, e.g. {\"location\":\"Chennai HQ\",\"assetCondition\":\"Fair\"}")
                    ), List.of("assetId", "fields")));

            tools.add(tool("delete_asset",
                    "PERMANENTLY delete an asset record. DESTRUCTIVE — the system will always ask the user to " +
                    "confirm before this actually runs, so you may call this as soon as the user's intent is clear; " +
                    "you do not need to ask 'are you sure' yourself, the platform handles that.",
                    params(prop("assetId", "integer", "Numeric asset ID")), List.of("assetId")));
        }

        tools.add(tool("assign_asset",
                "Assign an available asset to an employee. If the user names an asset by model (e.g. 'Dell Latitude " +
                "5440') rather than an ID, first use search_assets to find a matching AVAILABLE unit and confirm which " +
                "one with the user if more than one matches.",
                params(
                        prop("assetId", "integer", "Numeric asset ID to assign"),
                        prop("employeeId", "string", "Employee ID to assign it to (look up with search_employees if the user only gave a name)"),
                        prop("assignmentType", "string", "'Permanent' or 'Temporary', default Permanent"),
                        prop("temporaryReason", "string", "Required only if assignmentType is Temporary"),
                        prop("temporaryDurationDays", "integer", "Required only if assignmentType is Temporary"),
                        prop("remarks", "string", null)
                ), List.of("assetId", "employeeId")));

        tools.add(tool("return_asset",
                "Mark an assigned asset as returned by its current holder.",
                params(
                        prop("assetId", "integer", "Numeric asset ID"),
                        prop("reason", "string", "Why it's being returned")
                ), List.of("assetId")));

        if (isAdmin) {
            tools.add(tool("search_employees",
                    "Search/list employees — e.g. 'find employee John', 'list all employees in Finance'.",
                    params(
                            prop("keyword", "string", "Name, employee ID, department, or designation fragment")
                    ), List.of()));

            tools.add(tool("create_employee",
                    "Create a new employee record. Employee ID, name, and email are required.",
                    params(
                            prop("employeeId", "string", null),
                            prop("employeeName", "string", null),
                            prop("email", "string", null),
                            prop("department", "string", null),
                            prop("designation", "string", null),
                            prop("location", "string", null),
                            prop("joiningDate", "string", "YYYY-MM-DD")
                    ), List.of("employeeId", "employeeName", "email")));

            tools.add(tool("update_employee",
                    "Update an existing employee's profile fields (name, email, department, designation, location, " +
                    "phone/other fields on the record). Look the employee up first with search_employees if you " +
                    "only have a name, to get their numeric record id.",
                    params(
                            prop("id", "integer", "Numeric employee record id (NOT employeeId string)"),
                            prop("fields", "object", "Map of field name -> new value")
                    ), List.of("id", "fields")));

            tools.add(tool("delete_employee",
                    "PERMANENTLY delete an employee record. DESTRUCTIVE — confirmation is handled by the platform.",
                    params(prop("id", "integer", "Numeric employee record id")), List.of("id")));

            tools.add(tool("reset_employee_password",
                    "Reset an employee's password back to the organization default. DESTRUCTIVE — confirmation is " +
                    "handled by the platform.",
                    params(prop("employeeId", "string", null)), List.of("employeeId")));
        }

        tools.add(tool("schedule_maintenance",
                "Create a maintenance record/schedule for an asset.",
                params(
                        prop("assetId", "integer", null),
                        prop("maintenanceType", "string", "e.g. 'Battery replacement', 'OS reinstall'"),
                        prop("scheduledDate", "string", "YYYY-MM-DD"),
                        prop("notes", "string", null)
                ), List.of("assetId", "maintenanceType", "scheduledDate")));

        tools.add(tool("get_maintenance_due",
                "List maintenance records due soon or overdue — e.g. 'show maintenance due', 'what needs service soon'.",
                params(prop("withinDays", "integer", "Look-ahead window in days, default 30")), List.of()));

        if (isAdmin) {
            tools.add(tool("generate_report",
                    "Generate/export a report as PDF or Excel — e.g. 'generate monthly asset report', 'export to excel'. " +
                    "Returns a download link the user can click; it does not display the report inline.",
                    params(
                            prop("reportType", "string", "'employee_asset' or 'employee_exit'"),
                            prop("format", "string", "'pdf' or 'excel'")
                    ), List.of("reportType", "format")));

            tools.add(tool("email_report",
                    "Email a previously generated report (or a quick text summary) to one or more recipients — " +
                    "e.g. 'email this report to me'.",
                    params(
                            prop("toEmail", "string", null),
                            prop("subject", "string", null),
                            prop("summary", "string", "Plain-text/HTML body summarizing what's being sent")
                    ), List.of("toEmail", "subject", "summary")));
        }

        return tools;
    }

    // ── small JSON-schema builder helpers ─────────────────────────────────

    private static Map<String, Object> tool(String name, String description, Map<String, Object> parameters, List<String> required) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("type", "function");
        t.put("name", name);
        t.put("description", description);
        Map<String, Object> schema = new LinkedHashMap<>(parameters);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        t.put("parameters", schema);
        return t;
    }

    private static Map<String, Object> params(Map<String, Object>... properties) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (Map<String, Object> p : properties) {
            props.put((String) p.get("__name"), p.get("__schema"));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        return schema;
    }

    private static Map<String, Object> prop(String name, String type, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        if (description != null) schema.put("description", description);
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("__name", name);
        wrapper.put("__schema", schema);
        return wrapper;
    }
}
