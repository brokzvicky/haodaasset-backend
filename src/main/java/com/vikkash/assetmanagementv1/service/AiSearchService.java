package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.*;
import com.vikkash.assetmanagementv1.entity.AiSearchHistory;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.repository.AiSearchHistoryRepository;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the AI Search pipeline:
 *   natural language → AiIntentParserService (structured filters, never SQL)
 *   → role-scoped Specification/CriteriaBuilder query → PostgreSQL → JSON.
 *
 * Never executes AI-generated SQL — the only thing the parser hands back is
 * a whitelisted {@link AiSearchFilters} object; this class is the sole place
 * that turns those fields into a query, exactly like the existing
 * AssetService#search advanced-filters endpoint.
 */
@Service
public class AiSearchService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AssetRepository assetRepository;
    private final EmployeeRepository employeeRepository;
    private final AiIntentParserService intentParserService;
    private final AiSearchHistoryRepository historyRepository;

    public AiSearchService(AssetRepository assetRepository,
                            EmployeeRepository employeeRepository,
                            AiIntentParserService intentParserService,
                            AiSearchHistoryRepository historyRepository) {
        this.assetRepository = assetRepository;
        this.employeeRepository = employeeRepository;
        this.intentParserService = intentParserService;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public AiSearchResponse search(String rawQuery, boolean isAdmin, String callerId, String callerRole,
                                    Integer pageParam, Integer sizeParam) {

        AiIntentParserService.ParseResult parsed = intentParserService.parse(rawQuery);
        AiSearchFilters filters = parsed.filters;

        // ── Role scoping: employees only ever see their own assets, no matter
        //    what the free-text query asked for. Admins can search everything. ──
        if (!isAdmin) {
            filters.setDepartment(null);
            filters.setEmployeeName(null);
            filters.setUnassigned(null);
        }

        Specification<Asset> spec = buildSpecification(filters, isAdmin, callerId);
        List<Asset> allMatches = assetRepository.findAll(spec);

        // ── Pagination (in-memory over the already-filtered set; the filtered
        //    set itself is produced entirely by the DB via Specification) ──
        int size = (sizeParam != null && sizeParam > 0 && sizeParam <= 100) ? sizeParam : 24;
        int page = (pageParam != null && pageParam >= 0) ? pageParam : 0;
        int total = allMatches.size();
        int totalPages = size == 0 ? 0 : (int) Math.ceil(total / (double) size);
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<Asset> pageResults = allMatches.subList(from, to);

        Map<String, Long> statusBreakdown = allMatches.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getAssetStatus() == null ? "Unknown" : a.getAssetStatus(),
                        Collectors.counting()));

        String summary = buildSummary(rawQuery, filters, allMatches, statusBreakdown);

        AiSearchResponse response = new AiSearchResponse();
        response.setSummary(summary);
        response.setFilters(filters);
        response.setMatchedTerms(parsed.matchedTerms);
        response.setIgnoredTerms(parsed.ignoredTerms);
        response.setResults(pageResults);
        response.setResultCount(total);
        response.setPage(page);
        response.setSize(size);
        response.setTotalPages(totalPages);
        response.setStatusBreakdown(statusBreakdown);

        // NOTE: history is deliberately NOT recorded here. This method is
        // @Transactional(readOnly = true) (Postgres will reject writes on a
        // read-only JDBC connection), and calling recordHistory() on `this`
        // would bypass the Spring proxy anyway (self-invocation), so its own
        // @Transactional wouldn't open a fresh write transaction either. The
        // caller (AiSearchController) records history in a separate call
        // after this one returns.

        return response;
    }

    // ── Specification builder ────────────────────────────────────────────────

    private Specification<Asset> buildSpecification(AiSearchFilters f, boolean isAdmin, String callerId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Employees are hard-locked to their own asset(s) regardless of anything else.
            if (!isAdmin) {
                predicates.add(cb.equal(root.get("employeeId"), callerId));
            }

            if (f.getCategory() != null) {
                predicates.add(cb.equal(root.get("assetType"), f.getCategory()));
            }
            if (f.getBrand() != null) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), f.getBrand().toLowerCase(Locale.ROOT)));
            }
            if (f.getStatus() != null) {
                predicates.add(cb.equal(root.get("assetStatus"), f.getStatus()));
            }
            if (f.getBranch() != null) {
                predicates.add(cb.like(cb.lower(root.get("location")), "%" + f.getBranch().toLowerCase(Locale.ROOT) + "%"));
            }
            if (f.getRam() != null) {
                predicates.add(cb.like(cb.lower(root.get("ram")), "%" + f.getRam().toLowerCase(Locale.ROOT) + "%"));
            }
            if (f.getEmployeeName() != null) {
                predicates.add(cb.equal(cb.lower(root.get("employeeName")), f.getEmployeeName().toLowerCase(Locale.ROOT)));
            }
            if (Boolean.TRUE.equals(f.getUnassigned())) {
                predicates.add(cb.or(cb.isNull(root.get("employeeId")), cb.equal(root.get("employeeId"), "")));
            }
            if (f.getPurchaseYear() != null) {
                // purchaseDate is stored as an ISO "yyyy-MM-dd" string, so a prefix LIKE is safe & correct.
                predicates.add(cb.like(root.get("purchaseDate"), f.getPurchaseYear() + "-%"));
            }
            if (f.getWarrantyStatus() != null) {
                String today = LocalDate.now().format(ISO);
                switch (f.getWarrantyStatus()) {
                    case "Expired" -> predicates.add(cb.and(
                            cb.isNotNull(root.get("warrantyExpiry")),
                            cb.notEqual(root.get("warrantyExpiry"), ""),
                            cb.lessThan(root.get("warrantyExpiry"), today)
                    ));
                    case "ExpiringSoon" -> {
                        String in30 = LocalDate.now().plusDays(30).format(ISO);
                        predicates.add(cb.and(
                                cb.greaterThanOrEqualTo(root.get("warrantyExpiry"), today),
                                cb.lessThanOrEqualTo(root.get("warrantyExpiry"), in30)
                        ));
                    }
                    case "Active" -> predicates.add(cb.and(
                            cb.isNotNull(root.get("warrantyExpiry")),
                            cb.notEqual(root.get("warrantyExpiry"), ""),
                            cb.greaterThanOrEqualTo(root.get("warrantyExpiry"), today)
                    ));
                    default -> { /* unknown value — never trust unvalidated input, just ignore it */ }
                }
            }
            if (f.getDepartment() != null && isAdmin) {
                List<Employee> deptEmployees = employeeRepository.findByDepartmentContainingIgnoreCase(f.getDepartment());
                List<String> ids = deptEmployees.stream().map(Employee::getEmployeeId).collect(Collectors.toList());
                predicates.add(ids.isEmpty() ? cb.disjunction() : root.get("employeeId").in(ids));
            }
            if (f.getKeyword() != null) {
                String like = "%" + f.getKeyword().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("laptopName")), like),
                        cb.like(cb.lower(root.get("brand")), like),
                        cb.like(cb.lower(root.get("model")), like),
                        cb.like(cb.lower(root.get("serialNumber")), like),
                        cb.like(cb.lower(root.get("employeeName")), like),
                        cb.like(cb.lower(root.get("assetType")), like)
                ));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ── AI-style dynamic summary ────────────────────────────────────────────

    private String buildSummary(String rawQuery, AiSearchFilters f, List<Asset> matches, Map<String, Long> breakdown) {
        String invoiceNote = rawQuery.toLowerCase(Locale.ROOT).contains("invoice")
                ? "Invoice search isn't available yet — showing matching assets instead.\n\n"
                : "";

        if (matches.isEmpty()) {
            return invoiceNote + "I couldn't find any assets matching \"" + rawQuery.trim() + "\". Try removing a filter or checking the spelling.";
        }

        StringBuilder sb = new StringBuilder(invoiceNote);
        sb.append("I found ").append(matches.size()).append(" asset").append(matches.size() == 1 ? "" : "s");

        List<String> descriptors = new ArrayList<>();
        if (f.getBrand() != null) descriptors.add(f.getBrand());
        if (f.getCategory() != null) descriptors.add(f.getCategory().toLowerCase(Locale.ROOT) + (f.getCategory().endsWith("s") ? "" : "(s)"));
        if (!descriptors.isEmpty()) {
            sb.append(" — ").append(String.join(" ", descriptors));
        }
        if (f.getDepartment() != null) sb.append(" in ").append(f.getDepartment());
        if (f.getBranch() != null) sb.append(" at ").append(f.getBranch());
        if (f.getEmployeeName() != null) sb.append(" assigned to ").append(f.getEmployeeName());
        sb.append(".\n\n");

        long assigned = breakdown.getOrDefault("Assigned", 0L);
        long available = breakdown.getOrDefault("Available", 0L);
        long repair = breakdown.getOrDefault("Under Repair", 0L) + breakdown.getOrDefault("Faulty", 0L);
        long unassignedCount = matches.stream().filter(a -> a.getEmployeeId() == null || a.getEmployeeId().isBlank()).count();

        long expiringSoon = 0;
        long expired = 0;
        String today = LocalDate.now().format(ISO);
        String in30 = LocalDate.now().plusDays(30).format(ISO);
        for (Asset a : matches) {
            String w = a.getWarrantyExpiry();
            if (w == null || w.isBlank()) continue;
            try {
                if (w.compareTo(today) < 0) expired++;
                else if (w.compareTo(in30) <= 0) expiringSoon++;
            } catch (Exception ignored) { }
        }

        if (assigned > 0) sb.append("• ").append(assigned).append(" active (assigned)\n");
        if (available > 0) sb.append("• ").append(available).append(" available\n");
        if (expiringSoon > 0) sb.append("• ").append(expiringSoon).append(" warrant").append(expiringSoon == 1 ? "y" : "ies").append(" expiring within 30 days\n");
        if (expired > 0) sb.append("• ").append(expired).append(" warrant").append(expired == 1 ? "y" : "ies").append(" already expired\n");
        if (repair > 0) sb.append("• ").append(repair).append(" under maintenance/repair\n");
        if (unassignedCount > 0) sb.append("• ").append(unassignedCount).append(" currently unassigned\n");

        return sb.toString().trim();
    }

    // ── Search history persistence ──────────────────────────────────────────

    @Transactional
    public void recordHistory(String rawQuery, String callerId, String callerRole, int resultCount, List<String> matchedTerms) {
        AiSearchHistory h = new AiSearchHistory();
        h.setQuery(rawQuery.trim());
        h.setNormalizedQuery(rawQuery.trim().toLowerCase(Locale.ROOT));
        h.setPerformedBy(callerId);
        h.setPerformedByRole(callerRole);
        h.setResultCount(resultCount);
        h.setFiltersSummary(matchedTerms.isEmpty() ? null : String.join(", ", matchedTerms));
        historyRepository.save(h);
    }

    // ── Facets for the "Smart Suggestions" panel ─────────────────────────────

    @Transactional(readOnly = true)
    public AiSearchFacetsDTO facets(boolean isAdmin) {
        AiSearchFacetsDTO dto = new AiSearchFacetsDTO();
        dto.setBrands(assetRepository.findDistinctBrands());
        dto.setCategories(assetRepository.findDistinctAssetTypes());
        dto.setStatuses(assetRepository.findDistinctStatuses());
        dto.setBranches(assetRepository.findDistinctLocations());
        if (isAdmin) {
            dto.setDepartments(employeeRepository.findDistinctDepartments());
            dto.setEmployeeNames(assetRepository.findDistinctEmployeeNames());
        } else {
            dto.setDepartments(List.of());
            dto.setEmployeeNames(List.of());
        }
        return dto;
    }
}
