package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.AiSearchFilters;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a natural-language query into a validated {@link AiSearchFilters}
 * object — and NOTHING else. This is the entire "AI" in AI Search: a
 * deterministic, rule-based intent detector that only ever emits values
 * pulled from (and checked against) live database vocab. There is no code
 * path here that can produce raw SQL, and any word that can't be confidently
 * matched to a real field is dropped, not guessed.
 *
 * Pipeline: normalize → synonym-expand → typo-correct (Levenshtein against
 * live vocab) → pattern-match intents (warranty, unassigned, year, RAM,
 * "assigned to X") → whatever's left over becomes a loose keyword fallback.
 */
@Service
public class AiIntentParserService {

    private final AssetRepository assetRepository;
    private final EmployeeRepository employeeRepository;

    public AiIntentParserService(AssetRepository assetRepository, EmployeeRepository employeeRepository) {
        this.assetRepository = assetRepository;
        this.employeeRepository = employeeRepository;
    }

    // ── Synonym map (spec examples + common IT-asset phrasing) ─────────────
    private static final Map<String, String> SYNONYMS = new LinkedHashMap<>();
    static {
        SYNONYMS.put("notebook", "laptop");
        SYNONYMS.put("notebooks", "laptop");
        SYNONYMS.put("laptops", "laptop");
        SYNONYMS.put("pc", "desktop");
        SYNONYMS.put("pcs", "desktop");
        SYNONYMS.put("desktops", "desktop");
        SYNONYMS.put("device", "asset");
        SYNONYMS.put("devices", "asset");
        SYNONYMS.put("monitors", "monitor");
        SYNONYMS.put("keyboards", "keyboard");
        SYNONYMS.put("mice", "mouse");
        SYNONYMS.put("mouses", "mouse");
        SYNONYMS.put("staff", "employee");
        SYNONYMS.put("employees", "employee");
        SYNONYMS.put("teammate", "employee");
    }

    private static final Set<String> STATUS_SYNONYMS_REPAIR   = Set.of("maintenance", "repair", "broken", "faulty", "fixing", "servicing");
    private static final Set<String> STATUS_SYNONYMS_DISPOSED = Set.of("disposed", "scrapped", "junked", "trashed");
    private static final Set<String> STATUS_SYNONYMS_RETIRED  = Set.of("retired", "decommissioned");
    private static final Set<String> STATUS_SYNONYMS_LOST     = Set.of("lost", "missing", "misplaced");
    private static final Set<String> STATUS_SYNONYMS_SPARE    = Set.of("spare", "reserved", "reserve", "backup", "standby");
    private static final Set<String> STATUS_SYNONYMS_AVAIL    = Set.of("available", "unused", "free", "idle", "instock", "stock");
    private static final Set<String> STATUS_SYNONYMS_ASSIGNED = Set.of("assigned", "allotted", "given", "issued", "held");

    private static final Set<String> STOPWORDS = Set.of(
            "show", "all", "me", "find", "list", "get", "the", "a", "an", "with", "and", "for", "of", "in",
            "at", "to", "is", "are", "please", "any", "which", "who", "have", "has", "asset", "assets",
            "on", "by", "under", "that", "was", "were", "current", "currently"
    );

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern RAM_PATTERN  = Pattern.compile("\\b(\\d{1,3})\\s?gb\\b");
    private static final Pattern ASSIGNED_TO_PATTERN =
            Pattern.compile("(?:assigned to|belongs to|held by|with employee|owned by)\\s+([a-zA-Z .]+)");

    public static class ParseResult {
        public final AiSearchFilters filters = new AiSearchFilters();
        public final List<String> matchedTerms = new ArrayList<>();
        public final List<String> ignoredTerms = new ArrayList<>();
    }

    public ParseResult parse(String rawQuery) {
        ParseResult result = new ParseResult();
        if (rawQuery == null || rawQuery.isBlank()) return result;

        String q = rawQuery.toLowerCase(Locale.ROOT).trim()
                .replace("warranties", "warranty")
                .replace("branches", "branch")
                .replace("offices", "office");

        // Live vocab — every value the parser is allowed to match against.
        List<String> brands       = safe(assetRepository.findDistinctBrands());
        List<String> categories   = safe(assetRepository.findDistinctAssetTypes());
        List<String> branches     = safe(assetRepository.findDistinctLocations());
        List<String> statuses     = safe(assetRepository.findDistinctStatuses());
        List<String> employeeNms  = safe(assetRepository.findDistinctEmployeeNames());
        List<String> departments  = safe(employeeRepository.findDistinctDepartments());

        // ── 1. Warranty intent ─────────────────────────────────────────────
        if (q.contains("warranty")) {
            if (q.contains("expired") || (q.contains("expire") && q.contains("out"))) {
                result.filters.setWarrantyStatus("Expired");
                result.matchedTerms.add("Warranty: Expired");
            } else if (q.contains("expiring") || q.contains("about to expire") || q.contains("soon")) {
                result.filters.setWarrantyStatus("ExpiringSoon");
                result.matchedTerms.add("Warranty: Expiring within 30 days");
            } else if (q.contains("active") || q.contains("valid")) {
                result.filters.setWarrantyStatus("Active");
                result.matchedTerms.add("Warranty: Active");
            }
        } else if (q.contains("expired") && !q.contains("expired warranty")) {
            // "show expired warranties" without the word appearing right before — still catch it
            result.filters.setWarrantyStatus("Expired");
            result.matchedTerms.add("Warranty: Expired");
        }

        // ── 2. Unassigned intent ───────────────────────────────────────────
        if (q.contains("unassigned") || q.contains("not assigned") || q.contains("no employee")
                || q.contains("without employee") || q.contains("no owner")) {
            result.filters.setUnassigned(true);
            result.matchedTerms.add("Unassigned only");
        }

        // ── 3. Purchase year ────────────────────────────────────────────────
        Matcher yearMatcher = YEAR_PATTERN.matcher(q);
        if (yearMatcher.find()) {
            int year = Integer.parseInt(yearMatcher.group());
            result.filters.setPurchaseYear(year);
            result.matchedTerms.add("Purchased in " + year);
        }

        // ── 4. RAM ───────────────────────────────────────────────────────────
        Matcher ramMatcher = RAM_PATTERN.matcher(q);
        if (ramMatcher.find()) {
            String ram = ramMatcher.group(1);
            result.filters.setRam(ram);
            result.matchedTerms.add("RAM: " + ram + "GB");
        }

        // ── 5. Explicit "assigned to <name>" pattern ────────────────────────
        Matcher assignedMatcher = ASSIGNED_TO_PATTERN.matcher(q);
        if (assignedMatcher.find()) {
            String candidate = assignedMatcher.group(1).trim();
            String matchedName = bestFuzzyMatch(candidate, employeeNms);
            if (matchedName != null) {
                result.filters.setEmployeeName(matchedName);
                result.matchedTerms.add("Employee: " + matchedName);
            }
        }

        // ── 6. Department (checked before brand/category so "IT department"
        //       doesn't get swallowed by a generic token match) ─────────────
        String normalizedForDept = q.replace("it team", "it department").replace("it dept", "it department");
        for (String dept : departments) {
            if (dept == null || dept.isBlank()) continue;
            if (normalizedForDept.contains(dept.toLowerCase(Locale.ROOT))) {
                result.filters.setDepartment(dept);
                result.matchedTerms.add("Department: " + dept);
                break;
            }
        }

        // ── 7. Tokenize remainder for brand / category / branch / status / keyword ──
        String[] rawTokens = q.replaceAll("[^a-z0-9 ]", " ").split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String t : rawTokens) {
            if (t.isBlank()) continue;
            tokens.add(SYNONYMS.getOrDefault(t, t));
        }

        List<String> leftover = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (STOPWORDS.contains(token) || token.matches("\\d+") || token.equals("gb")) continue;

            // Branch / office (try token first, then a two-word phrase for "chennai kilpauk" style)
            if (result.filters.getBranch() == null) {
                String phrase2 = i + 1 < tokens.size() ? token + " " + tokens.get(i + 1) : token;
                String branchHit = containsMatch(phrase2, branches) != null ? containsMatch(phrase2, branches) : containsMatch(token, branches);
                if (branchHit != null) {
                    result.filters.setBranch(token); // partial match is applied at query time, keep it loose
                    result.matchedTerms.add("Branch: " + capitalize(token) + " (matches " + branchHit + ")");
                    continue;
                }
            }

            // Brand
            if (result.filters.getBrand() == null) {
                String brandHit = exactOrFuzzy(token, brands);
                if (brandHit != null) {
                    result.filters.setBrand(brandHit);
                    result.matchedTerms.add("Brand: " + brandHit);
                    continue;
                }
            }

            // Category / asset type
            if (result.filters.getCategory() == null) {
                String catHit = exactOrFuzzy(token, categories);
                if (catHit != null) {
                    result.filters.setCategory(catHit);
                    result.matchedTerms.add("Category: " + catHit);
                    continue;
                }
            }

            // Status (synonym groups first, then live vocab)
            if (result.filters.getStatus() == null) {
                String statusHit = matchStatus(token, statuses);
                if (statusHit != null) {
                    result.filters.setStatus(statusHit);
                    result.matchedTerms.add("Status: " + statusHit);
                    continue;
                }
            }

            // Employee name (single-token fallback, e.g. "Vikkash")
            if (result.filters.getEmployeeName() == null && token.length() >= 3) {
                String empHit = bestFuzzyMatch(token, employeeNms);
                if (empHit != null) {
                    result.filters.setEmployeeName(empHit);
                    result.matchedTerms.add("Employee: " + empHit);
                    continue;
                }
            }

            leftover.add(token);
        }

        // ── 8. Keyword fallback — only when nothing structured matched at all,
        //       so noisy leftover words never over-constrain a good match ──────
        if (result.filters.isEmpty() && !leftover.isEmpty()) {
            result.filters.setKeyword(String.join(" ", leftover));
            result.matchedTerms.add("Keyword: \"" + String.join(" ", leftover) + "\"");
        } else if (!result.filters.isEmpty()) {
            result.ignoredTerms.addAll(leftover);
        }

        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<String> safe(List<String> list) {
        return list == null ? List.of() : list;
    }

    /** Case-insensitive substring match either direction; returns the matched vocab value. */
    private static String containsMatch(String token, List<String> vocab) {
        for (String v : vocab) {
            if (v == null || v.isBlank()) continue;
            String lv = v.toLowerCase(Locale.ROOT);
            if (lv.contains(token) || token.contains(lv)) return v;
        }
        return null;
    }

    /** Exact (case-insensitive) match first, then a fuzzy (typo-tolerant) match. */
    private static String exactOrFuzzy(String token, List<String> vocab) {
        for (String v : vocab) {
            if (v != null && v.equalsIgnoreCase(token)) return v;
        }
        return bestFuzzyMatch(token, vocab);
    }

    private static String matchStatus(String token, List<String> liveStatuses) {
        if (STATUS_SYNONYMS_REPAIR.contains(token))   return preferLive(liveStatuses, "Under Repair", "Faulty");
        if (STATUS_SYNONYMS_DISPOSED.contains(token)) return preferLive(liveStatuses, "Disposed");
        if (STATUS_SYNONYMS_RETIRED.contains(token))  return preferLive(liveStatuses, "Retired");
        if (STATUS_SYNONYMS_LOST.contains(token))     return preferLive(liveStatuses, "Lost");
        if (STATUS_SYNONYMS_SPARE.contains(token))    return preferLive(liveStatuses, "Spare");
        if (STATUS_SYNONYMS_AVAIL.contains(token))    return preferLive(liveStatuses, "Available");
        if (STATUS_SYNONYMS_ASSIGNED.contains(token)) return preferLive(liveStatuses, "Assigned");
        return exactOrFuzzy(token, liveStatuses);
    }

    private static String preferLive(List<String> liveStatuses, String... candidates) {
        for (String c : candidates) {
            for (String live : liveStatuses) {
                if (live.equalsIgnoreCase(c)) return live;
            }
        }
        return candidates.length > 0 ? candidates[0] : null;
    }

    /**
     * Fuzzy match a candidate string/phrase against a vocab list using
     * Levenshtein distance (typo correction). Also matches a single-token
     * candidate (e.g. "vikkash") against any individual word inside a
     * multi-word vocab entry (e.g. "Vikkash Kumar" employee names), not just
     * the whole string — otherwise first-name-only queries would never hit.
     */
    private static String bestFuzzyMatch(String candidate, List<String> vocab) {
        if (candidate == null || candidate.isBlank() || vocab.isEmpty()) return null;
        String c = candidate.trim().toLowerCase(Locale.ROOT);
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String v : vocab) {
            if (v == null || v.isBlank()) continue;
            String lv = v.toLowerCase(Locale.ROOT);
            if (lv.equals(c)) return v;

            int maxAllowed = c.length() <= 4 ? 1 : 2;

            // Whole-string distance (handles single-word vocab entries).
            int dist = levenshtein(c, lv);
            if (dist <= maxAllowed && dist < bestDist) {
                bestDist = dist;
                best = v;
            }

            // Per-word distance (handles multi-word vocab entries like full names).
            for (String word : lv.split("\\s+")) {
                if (word.isBlank()) continue;
                if (word.equals(c)) return v;
                int wDist = levenshtein(c, word);
                if (wDist <= maxAllowed && wDist < bestDist) {
                    bestDist = wDist;
                    best = v;
                }
            }
        }
        return best;
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}
