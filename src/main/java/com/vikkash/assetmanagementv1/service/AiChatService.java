package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.AiChatEmployeeStat;
import com.vikkash.assetmanagementv1.dto.AiChatResponse;
import com.vikkash.assetmanagementv1.dto.AiSearchResponse;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.MaintenanceRecord;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.MaintenanceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The AI Chat Assistant / floating chat button (spec items #1 and #6).
 *
 * Same philosophy as {@link AiIntentParserService} / {@link AiSearchService}:
 * deterministic, rule-based pattern matching against real data — never an
 * LLM, never generated SQL. Question types not covered by simple "show me X"
 * filtering (asset lookup, "who has the most", maintenance due, warranty
 * this month) are handled here; anything else falls back to the existing
 * AiSearchService pipeline so "Show all Dell laptops" reuses the same
 * fuzzy/typo-tolerant matching the Smart Search box already has.
 */
@Service
public class AiChatService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AssetRepository assetRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final AiSearchService aiSearchService;

    public AiChatService(AssetRepository assetRepository,
                          MaintenanceRecordRepository maintenanceRecordRepository,
                          AiSearchService aiSearchService) {
        this.assetRepository = assetRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.aiSearchService = aiSearchService;
    }

    private static final Pattern LOCATE_PATTERN =
            Pattern.compile("(?:where\\s+is|where's|locate|find\\s+me)\\s+(?:the\\s+|my\\s+|asset\\s+)*([a-z0-9\\-\\/]{2,})");

    private static final Pattern OWNER_PATTERN =
            Pattern.compile("(?:who\\s+(?:owns|has)|which\\s+employee\\s+owns|owner\\s+of)\\s+(?:the\\s+|asset\\s+)*([a-z0-9\\-\\/]{2,})");

    private static final Pattern MOST_ASSETS_PATTERN =
            Pattern.compile("(?:most\\s+assets|who\\s+has\\s+the\\s+most|which\\s+employee\\s+has\\s+the\\s+most|top\\s+asset\\s+holder)");

    private static final Pattern MAINTENANCE_DUE_PATTERN =
            Pattern.compile("(?:due\\s+for\\s+maintenance|maintenance\\s+due|need(?:s)?\\s+(?:service|maintenance)|service\\s+soon|upcoming\\s+maintenance)");

    private static final Pattern WARRANTY_MONTH_PATTERN =
            Pattern.compile("warrant(?:y|ies).{0,20}(?:this\\s+month|expir\\w*\\s+this\\s+month)|(?:this\\s+month).{0,20}warrant");

    @Transactional(readOnly = true)
    public AiChatResponse answer(String rawMessage, boolean isAdmin, String callerId) {
        String q = rawMessage == null ? "" : rawMessage.toLowerCase(Locale.ROOT).trim();
        AiChatResponse response = new AiChatResponse();

        if (q.isBlank()) {
            response.setAnswer("Ask me something like \"where is asset 102\" or \"show unassigned laptops\".");
            response.setSuggestions(defaultSuggestions(isAdmin));
            return response;
        }

        // ── 1. Locate / "where is X" ────────────────────────────────────────
        Matcher locate = LOCATE_PATTERN.matcher(q);
        if (locate.find()) {
            Asset match = findAssetByToken(locate.group(1), isAdmin, callerId);
            if (match != null) return locateAnswer(match);
        }

        // ── 2. Ownership / "who owns X" ─────────────────────────────────────
        Matcher owner = OWNER_PATTERN.matcher(q);
        if (owner.find()) {
            Asset match = findAssetByToken(owner.group(1), isAdmin, callerId);
            if (match != null) return ownerAnswer(match);
        }

        // ── 3. "Which employee has the most assets" (admin only — org-wide) ──
        if (MOST_ASSETS_PATTERN.matcher(q).find()) {
            if (!isAdmin) {
                response.setAnswer("That's an org-wide view, available to admins. I can show you your own assigned assets instead — just ask \"show my assets\".");
                return response;
            }
            return mostAssetsAnswer();
        }

        // ── 4. Maintenance due ───────────────────────────────────────────────
        if (MAINTENANCE_DUE_PATTERN.matcher(q).find()) {
            return maintenanceDueAnswer(isAdmin, callerId);
        }

        // ── 5. Warranty expiring this month ─────────────────────────────────
        if (WARRANTY_MONTH_PATTERN.matcher(q).find()) {
            return warrantyThisMonthAnswer(isAdmin, callerId);
        }

        // ── 6. Fallback: reuse the existing Smart Search intent pipeline ────
        return fallbackToSearch(rawMessage, isAdmin, callerId);
    }

    // ── Intent handlers ──────────────────────────────────────────────────────

    private Asset findAssetByToken(String token, boolean isAdmin, String callerId) {
        if (token == null || token.isBlank()) return null;
        String cleaned = token.trim();

        List<Asset> candidates = new ArrayList<>();

        // Numeric-looking token: try it as the primary key first.
        String numeric = cleaned.replaceAll("[^0-9]", "");
        if (!numeric.isBlank()) {
            assetRepository.findById(Long.parseLong(numeric)).ifPresent(candidates::add);
        }
        if (candidates.isEmpty()) {
            candidates.addAll(assetRepository.findBySerialNumberContainingIgnoreCase(cleaned));
        }
        if (candidates.isEmpty()) {
            candidates.addAll(assetRepository.findByLaptopNameContainingIgnoreCase(cleaned));
        }
        if (candidates.isEmpty()) return null;

        if (!isAdmin) {
            candidates = candidates.stream()
                    .filter(a -> callerId.equals(a.getEmployeeId()))
                    .collect(Collectors.toList());
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private AiChatResponse locateAnswer(Asset a) {
        AiChatResponse r = new AiChatResponse();
        r.setType("ASSETS");
        r.setAssets(List.of(a));
        StringBuilder sb = new StringBuilder();
        sb.append(a.getLaptopName()).append(" (").append(a.getBrand());
        if (a.getModel() != null && !a.getModel().isBlank()) sb.append(" ").append(a.getModel());
        sb.append(", S/N ").append(a.getSerialNumber() == null ? "—" : a.getSerialNumber()).append(") ");
        if (a.getLocation() != null && !a.getLocation().isBlank()) {
            sb.append("is at ").append(a.getLocation()).append(". ");
        } else {
            sb.append("has no location on file. ");
        }
        if (a.getEmployeeName() != null && !a.getEmployeeName().isBlank()) {
            sb.append("Currently assigned to ").append(a.getEmployeeName()).append(".");
        } else {
            sb.append("Currently unassigned, status: ").append(a.getAssetStatus()).append(".");
        }
        r.setAnswer(sb.toString());
        return r;
    }

    private AiChatResponse ownerAnswer(Asset a) {
        AiChatResponse r = new AiChatResponse();
        r.setType("ASSETS");
        r.setAssets(List.of(a));
        if (a.getEmployeeName() != null && !a.getEmployeeName().isBlank()) {
            r.setAnswer(a.getEmployeeName() + " (" + a.getEmployeeId() + ") currently owns " + a.getLaptopName()
                    + ", serial " + (a.getSerialNumber() == null ? "—" : a.getSerialNumber()) + ".");
        } else {
            r.setAnswer(a.getLaptopName() + " is currently unassigned"
                    + (a.getLastEmployeeName() != null ? " — last held by " + a.getLastEmployeeName() + "." : "."));
        }
        return r;
    }

    private AiChatResponse mostAssetsAnswer() {
        List<Object[]> rows = assetRepository.countAssetsGroupedByEmployee();
        AiChatResponse r = new AiChatResponse();
        if (rows.isEmpty()) {
            r.setAnswer("No assets are currently assigned to anyone.");
            return r;
        }
        List<AiChatEmployeeStat> top = rows.stream()
                .limit(5)
                .map(row -> new AiChatEmployeeStat((String) row[0], (String) row[1], null, (Long) row[2]))
                .collect(Collectors.toList());
        r.setType("EMPLOYEES");
        r.setEmployees(top);
        AiChatEmployeeStat leader = top.get(0);
        r.setAnswer(leader.getEmployeeName() + " (" + leader.getEmployeeId() + ") holds the most assets — "
                + leader.getAssetCount() + " in total.");
        return r;
    }

    private AiChatResponse maintenanceDueAnswer(boolean isAdmin, String callerId) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(30);

        List<MaintenanceRecord> upcoming = maintenanceRecordRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(m -> !"Completed".equalsIgnoreCase(m.getStatus()) && !"Cancelled".equalsIgnoreCase(m.getStatus()))
                .filter(m -> {
                    LocalDate due = parseDate(m.getNextMaintenanceDate());
                    if (due == null) due = parseDate(m.getScheduledDate());
                    return due != null && !due.isAfter(horizon);
                })
                .collect(Collectors.toList());

        if (!isAdmin) {
            Set<Long> ownAssetIds = assetRepository.findByEmployeeId(callerId).stream()
                    .map(Asset::getAssetId).collect(Collectors.toSet());
            upcoming = upcoming.stream().filter(m -> ownAssetIds.contains(m.getAssetId())).collect(Collectors.toList());
        }

        AiChatResponse r = new AiChatResponse();
        r.setType("MAINTENANCE");
        r.setMaintenanceRecords(upcoming);
        if (upcoming.isEmpty()) {
            r.setAnswer(isAdmin ? "Nothing is due for maintenance in the next 30 days." : "None of your assets are due for maintenance in the next 30 days.");
        } else {
            r.setAnswer(upcoming.size() + " asset" + (upcoming.size() == 1 ? "" : "s") + " due for maintenance within 30 days.");
        }
        return r;
    }

    private AiChatResponse warrantyThisMonthAnswer(boolean isAdmin, String callerId) {
        YearMonth thisMonth = YearMonth.now();
        List<Asset> expiring = assetRepository.findAllWithWarrantyDate().stream()
                .filter(a -> {
                    LocalDate d = parseDate(a.getWarrantyExpiry());
                    return d != null && YearMonth.from(d).equals(thisMonth);
                })
                .filter(a -> isAdmin || callerId.equals(a.getEmployeeId()))
                .collect(Collectors.toList());

        AiChatResponse r = new AiChatResponse();
        r.setType("ASSETS");
        r.setAssets(expiring);
        r.setAnswer(expiring.isEmpty()
                ? "No warranties expire this month."
                : expiring.size() + " warrant" + (expiring.size() == 1 ? "y expires" : "ies expire") + " this month.");
        return r;
    }

    private AiChatResponse fallbackToSearch(String rawMessage, boolean isAdmin, String callerId) {
        AiSearchResponse searchResult = aiSearchService.search(rawMessage, isAdmin, callerId,
                isAdmin ? "ADMIN" : "EMPLOYEE", 0, 8);

        AiChatResponse r = new AiChatResponse();
        r.setType("ASSETS");
        r.setAssets(searchResult.getResults());
        if (searchResult.getResultCount() == 0) {
            r.setAnswer("I couldn't find anything matching that. Try rephrasing, or ask about a specific asset by name/serial number.");
            r.setSuggestions(defaultSuggestions(isAdmin));
        } else {
            r.setAnswer(searchResult.getSummary());
        }
        return r;
    }

    private List<String> defaultSuggestions(boolean isAdmin) {
        if (isAdmin) {
            return List.of("Which employee has the most assets?", "Which warranties expire this month?",
                    "Show unassigned laptops", "Which assets are due for maintenance?");
        }
        return List.of("Show my assets", "Where is my laptop?", "Is my warranty still active?");
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
