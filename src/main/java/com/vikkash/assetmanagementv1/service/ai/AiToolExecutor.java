package com.vikkash.assetmanagementv1.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikkash.assetmanagementv1.dto.AiSearchResponse;
import com.vikkash.assetmanagementv1.dto.AssignAssetRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeCreateRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeUpdateRequest;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.entity.MaintenanceRecord;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import com.vikkash.assetmanagementv1.service.AiSearchService;
import com.vikkash.assetmanagementv1.service.AssetService;
import com.vikkash.assetmanagementv1.service.EmailService;
import com.vikkash.assetmanagementv1.service.EmployeeService;
import com.vikkash.assetmanagementv1.service.MaintenanceService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Runs exactly one tool call and returns a plain object that gets JSON-
 * serialized straight back to the model as the function_call_output. Every
 * branch below calls an existing service method used by the ordinary REST
 * controllers — the AI assistant is a new caller of old, already-tested
 * business logic, never a parallel implementation of it.
 *
 * Destructive tools are listed in {@link #DESTRUCTIVE_TOOLS}; the orchestrator
 * checks that set BEFORE calling {@link #execute} and routes those through a
 * user-confirmation step first (see AiAssistantOrchestrator).
 */
@Component
public class AiToolExecutor {

    public static final Set<String> DESTRUCTIVE_TOOLS = Set.of(
            "delete_asset", "delete_employee", "reset_employee_password");

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AssetService assetService;
    private final AssetRepository assetRepository;
    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final MaintenanceService maintenanceService;
    private final AiSearchService aiSearchService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public AiToolExecutor(AssetService assetService, AssetRepository assetRepository,
                           EmployeeService employeeService, EmployeeRepository employeeRepository,
                           MaintenanceService maintenanceService, AiSearchService aiSearchService,
                           EmailService emailService, ObjectMapper objectMapper) {
        this.assetService = assetService;
        this.assetRepository = assetRepository;
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
        this.maintenanceService = maintenanceService;
        this.aiSearchService = aiSearchService;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    /** Human-readable confirmation prompt shown before a destructive tool actually runs. */
    public String describeForConfirmation(String toolName, JsonNode args) {
        return switch (toolName) {
            case "delete_asset" -> {
                Long id = args.path("assetId").asLong();
                Asset a = assetRepository.findById(id).orElse(null);
                yield a == null
                        ? "Delete asset #" + id + "? This cannot be undone."
                        : "Delete **" + a.getLaptopName() + "** (S/N " + safe(a.getSerialNumber()) + ", asset #" + id + ")? This cannot be undone.";
            }
            case "delete_employee" -> {
                Long id = args.path("id").asLong();
                Employee e = employeeRepository.findById(id).orElse(null);
                yield e == null
                        ? "Delete employee record #" + id + "? This cannot be undone."
                        : "Delete employee **" + e.getEmployeeName() + "** (" + e.getEmployeeId() + ")? Their asset history will remain, but the account itself is permanently removed.";
            }
            case "reset_employee_password" -> "Reset the password for employee **" + args.path("employeeId").asText() +
                    "** back to the organization default? They'll be required to change it on next login.";
            default -> "Proceed with " + toolName + "?";
        };
    }

    public Object execute(String toolName, JsonNode args, boolean isAdmin, String callerId) {
        try {
            return switch (toolName) {
                case "search_assets" -> searchAssets(args, isAdmin, callerId);
                case "get_asset_details" -> getAssetDetails(args, isAdmin, callerId);
                case "create_asset" -> requireAdmin(isAdmin, () -> createAsset(args));
                case "update_asset" -> updateAsset(args);
                case "delete_asset" -> requireAdmin(isAdmin, () -> deleteAsset(args));
                case "assign_asset" -> assignAsset(args);
                case "return_asset" -> returnAsset(args);
                case "search_employees" -> requireAdmin(isAdmin, () -> searchEmployees(args));
                case "create_employee" -> requireAdmin(isAdmin, () -> createEmployee(args));
                case "update_employee" -> requireAdmin(isAdmin, () -> updateEmployee(args));
                case "delete_employee" -> requireAdmin(isAdmin, () -> deleteEmployee(args));
                case "reset_employee_password" -> requireAdmin(isAdmin, () -> resetPassword(args));
                case "schedule_maintenance" -> scheduleMaintenance(args, callerId);
                case "get_maintenance_due" -> maintenanceDue(args, isAdmin, callerId);
                case "generate_report" -> requireAdmin(isAdmin, () -> generateReport(args));
                case "email_report" -> requireAdmin(isAdmin, () -> emailReport(args));
                default -> Map.of("error", "Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            return Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private <T> T requireAdmin(boolean isAdmin, java.util.function.Supplier<T> action) {
        if (!isAdmin) throw new IllegalStateException("This action requires admin privileges.");
        return action.get();
    }

    // ── Assets ──────────────────────────────────────────────────────────────

    private Object searchAssets(JsonNode args, boolean isAdmin, String callerId) {
        boolean onlyUnused = args.path("onlyUnused").asBoolean(false);
        boolean purchasedThisYear = args.path("purchasedThisYear").asBoolean(false);
        boolean warrantyThisMonth = args.path("warrantyExpiringThisMonth").asBoolean(false);
        boolean duplicatesOnly = args.path("duplicateSerialsOnly").asBoolean(false);

        List<Asset> results;

        if (duplicatesOnly) {
            Map<String, List<Asset>> bySerial = assetRepository.findAll().stream()
                    .filter(a -> a.getSerialNumber() != null && !a.getSerialNumber().isBlank())
                    .collect(Collectors.groupingBy(a -> a.getSerialNumber().trim().toUpperCase()));
            results = bySerial.values().stream().filter(l -> l.size() > 1).flatMap(List::stream).collect(Collectors.toList());
        } else if (onlyUnused) {
            results = assetRepository.findByAssetStatus("Available").stream()
                    .filter(a -> a.getLastEmployeeId() == null || a.getLastEmployeeId().isBlank())
                    .collect(Collectors.toList());
        } else {
            String keyword = args.path("keyword").asText(null);
            String assetType = args.path("assetType").asText(null);
            String assetStatus = args.path("assetStatus").asText(null);
            String assetCondition = args.path("assetCondition").asText(null);
            results = assetService.search(keyword, assetType, assetStatus, assetCondition,
                    null, null, null, null, null, null, null);

            // Plain search() does exact/contains matching. If that comes back
            // empty but the model passed a free-text keyword, fall back to the
            // existing fuzzy/typo-tolerant Smart Search pipeline (AiSearchService)
            // instead of reimplementing fuzzy matching here.
            if (results.isEmpty() && keyword != null && !keyword.isBlank()) {
                AiSearchResponse fuzzy = aiSearchService.search(keyword, isAdmin, callerId,
                        isAdmin ? "ADMIN" : "EMPLOYEE", 0, 25);
                results = fuzzy.getResults();
            }

            if (purchasedThisYear) {
                int year = LocalDate.now().getYear();
                results = results.stream().filter(a -> {
                    LocalDate d = parseDate(a.getPurchaseDate());
                    return d != null && d.getYear() == year;
                }).collect(Collectors.toList());
            }
            if (warrantyThisMonth) {
                YearMonth thisMonth = YearMonth.now();
                results = results.stream().filter(a -> {
                    LocalDate d = parseDate(a.getWarrantyExpiry());
                    return d != null && YearMonth.from(d).equals(thisMonth);
                }).collect(Collectors.toList());
            }
        }

        if (!isAdmin) {
            results = results.stream().filter(a -> callerId.equals(a.getEmployeeId())).collect(Collectors.toList());
        }

        return Map.of("count", results.size(), "assets", trimAssets(results, 25));
    }

    private Object getAssetDetails(JsonNode args, boolean isAdmin, String callerId) {
        String token = args.path("identifier").asText("");
        List<Asset> candidates = new ArrayList<>();
        String numeric = token.replaceAll("[^0-9]", "");
        if (!numeric.isBlank()) {
            assetRepository.findById(Long.parseLong(numeric)).ifPresent(candidates::add);
        }
        if (candidates.isEmpty()) candidates.addAll(assetRepository.findBySerialNumberContainingIgnoreCase(token));
        if (candidates.isEmpty()) candidates.addAll(assetRepository.findByLaptopNameContainingIgnoreCase(token));

        if (!isAdmin) {
            candidates = candidates.stream().filter(a -> callerId.equals(a.getEmployeeId())).collect(Collectors.toList());
        }
        if (candidates.isEmpty()) return Map.of("found", false, "message", "No asset matched '" + token + "'.");
        return Map.of("found", true, "asset", candidates.get(0));
    }

    private Object createAsset(JsonNode args) {
        Asset asset = new Asset();
        asset.setLaptopName(args.path("laptopName").asText(null));
        asset.setBrand(args.path("brand").asText(null));
        asset.setModel(args.path("model").asText(null));
        asset.setAssetType(args.path("assetType").asText("Laptop"));
        asset.setSerialNumber(args.path("serialNumber").asText(null));
        asset.setVendor(args.path("vendor").asText(null));
        asset.setAssetCost(args.path("assetCost").asText(null));
        asset.setPurchaseDate(args.path("purchaseDate").asText(null));
        asset.setWarrantyExpiry(args.path("warrantyExpiry").asText(null));
        asset.setLocation(args.path("location").asText(null));
        asset.setProcessor(args.path("processor").asText(null));
        asset.setRam(args.path("ram").asText(null));
        asset.setStorage(args.path("storage").asText(null));
        Asset saved = assetService.createAsset(asset);
        return Map.of("created", true, "asset", saved);
    }

    private Object updateAsset(JsonNode args) {
        Long id = args.path("assetId").asLong();
        JsonNode fields = args.path("fields");
        Asset patch = new Asset();
        fields.fields().forEachRemaining(e -> setAssetField(patch, e.getKey(), e.getValue().asText(null)));
        Asset saved = assetService.updateAsset(id, patch);
        return Map.of("updated", true, "asset", saved);
    }

    private void setAssetField(Asset a, String field, String value) {
        switch (field) {
            case "laptopName" -> a.setLaptopName(value);
            case "brand" -> a.setBrand(value);
            case "model" -> a.setModel(value);
            case "assetType" -> a.setAssetType(value);
            case "serialNumber" -> a.setSerialNumber(value);
            case "location" -> a.setLocation(value);
            case "vendor" -> a.setVendor(value);
            case "assetCost" -> a.setAssetCost(value);
            case "purchaseDate" -> a.setPurchaseDate(value);
            case "warrantyExpiry" -> a.setWarrantyExpiry(value);
            case "remarks" -> a.setRemarks(value);
            case "assetCondition" -> a.setAssetCondition(value);
            default -> { /* unknown field name from the model — ignore rather than fail the whole update */ }
        }
    }

    private Object deleteAsset(JsonNode args) {
        Long id = args.path("assetId").asLong();
        assetService.deleteAsset(id);
        return Map.of("deleted", true, "assetId", id);
    }

    private Object assignAsset(JsonNode args) {
        Long assetId = args.path("assetId").asLong();
        AssignAssetRequest req = new AssignAssetRequest();
        req.setEmployeeId(args.path("employeeId").asText());
        req.setAssignmentType(args.path("assignmentType").asText("Permanent"));
        req.setTemporaryReason(args.path("temporaryReason").asText(null));
        if (args.has("temporaryDurationDays") && !args.get("temporaryDurationDays").isNull()) {
            req.setTemporaryDurationDays(args.get("temporaryDurationDays").asInt());
        }
        req.setRemarks(args.path("remarks").asText(null));
        Asset saved = assetService.assignAsset(assetId, req);
        return Map.of("assigned", true, "asset", saved);
    }

    private Object returnAsset(JsonNode args) {
        Long assetId = args.path("assetId").asLong();
        Map<String, String> body = new HashMap<>();
        body.put("reason", args.path("reason").asText(""));
        Asset saved = assetService.returnAsset(assetId, body);
        return Map.of("returned", true, "asset", saved);
    }

    // ── Employees ───────────────────────────────────────────────────────────

    private Object searchEmployees(JsonNode args) {
        String keyword = args.path("keyword").asText("");
        List<Employee> all = employeeService.getAllEmployees();
        String kw = keyword.toLowerCase(Locale.ROOT);
        List<Employee> filtered = kw.isBlank() ? all : all.stream().filter(e ->
                        (e.getEmployeeName() != null && e.getEmployeeName().toLowerCase(Locale.ROOT).contains(kw)) ||
                        (e.getEmployeeId() != null && e.getEmployeeId().toLowerCase(Locale.ROOT).contains(kw)) ||
                        (e.getDepartment() != null && e.getDepartment().toLowerCase(Locale.ROOT).contains(kw)) ||
                        (e.getDesignation() != null && e.getDesignation().toLowerCase(Locale.ROOT).contains(kw)))
                .collect(Collectors.toList());
        return Map.of("count", filtered.size(), "employees", filtered.stream().limit(25).collect(Collectors.toList()));
    }

    private Object createEmployee(JsonNode args) {
        EmployeeCreateRequest req = new EmployeeCreateRequest();
        req.setEmployeeId(args.path("employeeId").asText());
        req.setEmployeeName(args.path("employeeName").asText());
        req.setEmail(args.path("email").asText());
        req.setDepartment(args.path("department").asText(null));
        req.setDesignation(args.path("designation").asText(null));
        req.setLocation(args.path("location").asText(null));
        req.setJoiningDate(args.path("joiningDate").asText(null));
        Employee saved = employeeService.createEmployee(req);
        return Map.of("created", true, "employee", saved);
    }

    private Object updateEmployee(JsonNode args) {
        Long id = args.path("id").asLong();
        Employee existing = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No employee with id " + id));

        // Pre-fill from the current record — updateEmployee() requires
        // employeeId/employeeName/email to always be present, so a partial
        // {"fields": {"location": "..."}} patch must be merged onto the
        // existing values rather than sent as a bare partial object.
        EmployeeUpdateRequest req = new EmployeeUpdateRequest();
        req.setEmployeeId(existing.getEmployeeId());
        req.setEmployeeName(existing.getEmployeeName());
        req.setEmail(existing.getEmail());
        req.setDepartment(existing.getDepartment());
        req.setDesignation(existing.getDesignation());
        req.setLocation(existing.getLocation());
        req.setJoiningDate(existing.getJoiningDate());

        JsonNode fields = args.path("fields");
        fields.fields().forEachRemaining(e -> {
            String v = e.getValue().asText(null);
            switch (e.getKey()) {
                case "employeeId" -> req.setEmployeeId(v);
                case "employeeName" -> req.setEmployeeName(v);
                case "email" -> req.setEmail(v);
                case "department" -> req.setDepartment(v);
                case "designation" -> req.setDesignation(v);
                case "location" -> req.setLocation(v);
                case "joiningDate" -> req.setJoiningDate(v);
                default -> { /* ignore unrecognized field names */ }
            }
        });

        Employee saved = employeeService.updateEmployee(id, req);
        return Map.of("updated", true, "employee", saved);
    }

    private Object deleteEmployee(JsonNode args) {
        Long id = args.path("id").asLong();
        employeeService.deleteEmployee(id);
        return Map.of("deleted", true, "id", id);
    }

    private Object resetPassword(JsonNode args) {
        String employeeId = args.path("employeeId").asText();
        employeeService.resetToDefaultPassword(employeeId);
        return Map.of("reset", true, "employeeId", employeeId);
    }

    // ── Maintenance ─────────────────────────────────────────────────────────

    private Object scheduleMaintenance(JsonNode args, String callerId) {
        MaintenanceRecord record = new MaintenanceRecord();
        record.setAssetId(args.path("assetId").asLong());
        record.setMaintenanceType(args.path("maintenanceType").asText("Preventive"));
        record.setScheduledDate(args.path("scheduledDate").asText(null));
        record.setRemarks(args.path("notes").asText(null));
        MaintenanceRecord saved = maintenanceService.create(record, callerId);
        return Map.of("scheduled", true, "record", saved);
    }

    private Object maintenanceDue(JsonNode args, boolean isAdmin, String callerId) {
        int withinDays = args.path("withinDays").asInt(30);
        LocalDate horizon = LocalDate.now().plusDays(withinDays);
        List<MaintenanceRecord> upcoming = maintenanceService.getAll().stream()
                .filter(m -> !"Completed".equalsIgnoreCase(m.getStatus()) && !"Cancelled".equalsIgnoreCase(m.getStatus()))
                .filter(m -> {
                    LocalDate due = parseDate(m.getNextMaintenanceDate());
                    if (due == null) due = parseDate(m.getScheduledDate());
                    return due != null && !due.isAfter(horizon);
                }).collect(Collectors.toList());

        if (!isAdmin) {
            Set<Long> ownAssetIds = assetRepository.findByEmployeeId(callerId).stream()
                    .map(Asset::getAssetId).collect(Collectors.toSet());
            upcoming = upcoming.stream().filter(m -> ownAssetIds.contains(m.getAssetId())).collect(Collectors.toList());
        }
        return Map.of("count", upcoming.size(), "records", upcoming);
    }

    // ── Reports / email ───────────────────────────────────────────────────

    private Object generateReport(JsonNode args) {
        String reportType = args.path("reportType").asText("");
        String format = args.path("format").asText("pdf").toLowerCase(Locale.ROOT);

        String url = switch (reportType + ":" + format) {
            case "employee_asset:pdf" -> "/api/admin/reports/employee-asset-report/pdf";
            case "employee_exit:pdf" -> "/api/admin/reports/employee-exit-report/pdf";
            case "employee_exit:excel" -> "/api/admin/reports/employee-exit-report/excel";
            default -> null;
        };
        if (url == null) {
            return Map.of("available", false,
                    "message", "That combination isn't available yet. Available reports: " +
                            "employee_asset (pdf), employee_exit (pdf, excel).");
        }
        return Map.of("available", true, "downloadUrl", url,
                "note", "This is a relative API path — the frontend should call it with the signed-in admin's existing auth headers.");
    }

    private Object emailReport(JsonNode args) {
        String to = args.path("toEmail").asText();
        String subject = args.path("subject").asText("Asset Management Report");
        String summary = args.path("summary").asText("");
        emailService.sendSimpleNotificationEmail(to, subject, subject, "<p>" + summary + "</p>");
        return Map.of("sent", true, "to", to);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private List<Asset> trimAssets(List<Asset> assets, int max) {
        return assets.size() > max ? assets.subList(0, max) : assets;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim(), ISO);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
