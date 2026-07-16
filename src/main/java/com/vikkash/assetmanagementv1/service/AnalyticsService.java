package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import com.vikkash.assetmanagementv1.repository.MaintenanceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reports & Analytics: aggregated, chart-ready statistics computed live
 * from the real asset/employee/maintenance tables — no mock data. Backs
 * the enhanced Dashboard widgets and the Reports & Analytics page.
 */
@Service
public class AnalyticsService {

    private final AssetRepository assetRepository;
    private final EmployeeRepository employeeRepository;
    private final MaintenanceRecordRepository maintenanceRepository;

    public AnalyticsService(AssetRepository assetRepository,
                             EmployeeRepository employeeRepository,
                             MaintenanceRecordRepository maintenanceRepository) {
        this.assetRepository = assetRepository;
        this.employeeRepository = employeeRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAnalytics() {
        List<Asset> assets = assetRepository.findAll();
        Map<String, Object> result = new HashMap<>();

        result.put("totalAssets", assets.size());
        result.put("totalEmployees", employeeRepository.count());

        result.put("byStatus", groupCount(assets, a -> nullToUnknown(a.getAssetStatus())));
        result.put("byType", groupCount(assets, a -> nullToUnknown(a.getAssetType())));
        result.put("byCondition", groupCount(assets, a -> nullToUnknown(a.getAssetCondition())));
        result.put("byLocation", groupCount(assets, a -> nullToUnknown(a.getLocation())));
        result.put("byBrand", groupCount(assets, a -> nullToUnknown(a.getBrand())));

        result.put("totalAssetValue", assets.stream()
                .map(Asset::getAssetCost)
                .filter(c -> c != null && !c.isBlank())
                .mapToDouble(this::parseMoneyOrZero)
                .sum());

        result.put("warrantyExpiringSoon", warrantyExpiringWithin(30, assets));
        result.put("warrantyExpired", warrantyExpiredCount(assets));

        result.put("maintenanceStats", Map.of(
                "scheduled", maintenanceRepository.countByStatus("Scheduled"),
                "inProgress", maintenanceRepository.countByStatus("In Progress"),
                "completed", maintenanceRepository.countByStatus("Completed"),
                "cancelled", maintenanceRepository.countByStatus("Cancelled")
        ));

        result.put("temporaryAssignments", assets.stream()
                .filter(a -> "Temporary".equalsIgnoreCase(a.getAssignmentType()) && "Assigned".equals(a.getAssetStatus()))
                .count());

        // Assets by age bracket (based on purchaseDate), useful for refresh/replacement planning
        result.put("byAgeBracket", ageBrackets(assets));

        return result;
    }

    private Map<String, Long> groupCount(List<Asset> assets, java.util.function.Function<Asset, String> classifier) {
        return assets.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }

    private String nullToUnknown(String value) {
        return (value == null || value.isBlank()) ? "Unspecified" : value;
    }

    private double parseMoneyOrZero(String raw) {
        try {
            return Double.parseDouble(raw.replaceAll("[^0-9.]", ""));
        } catch (Exception ex) {
            return 0d;
        }
    }

    private List<Map<String, Object>> warrantyExpiringWithin(int days, List<Asset> assets) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(days);
        List<Map<String, Object>> list = new ArrayList<>();

        for (Asset a : assets) {
            if (a.getWarrantyExpiry() == null || a.getWarrantyExpiry().isBlank()) continue;
            try {
                LocalDate expiry = LocalDate.parse(a.getWarrantyExpiry());
                if (!expiry.isBefore(today) && !expiry.isAfter(horizon)) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("assetId", a.getAssetId());
                    row.put("laptopName", a.getLaptopName());
                    row.put("serialNumber", a.getSerialNumber());
                    row.put("warrantyExpiry", a.getWarrantyExpiry());
                    row.put("daysLeft", java.time.temporal.ChronoUnit.DAYS.between(today, expiry));
                    list.add(row);
                }
            } catch (Exception ignored) { }
        }
        list.sort(Comparator.comparing(m -> (Long) m.get("daysLeft")));
        return list;
    }

    private long warrantyExpiredCount(List<Asset> assets) {
        LocalDate today = LocalDate.now();
        return assets.stream().filter(a -> {
            if (a.getWarrantyExpiry() == null || a.getWarrantyExpiry().isBlank()) return false;
            try {
                return LocalDate.parse(a.getWarrantyExpiry()).isBefore(today);
            } catch (Exception ex) {
                return false;
            }
        }).count();
    }

    private Map<String, Long> ageBrackets(List<Asset> assets) {
        LocalDate today = LocalDate.now();
        Map<String, Long> brackets = new HashMap<>();
        brackets.put("< 1 year", 0L);
        brackets.put("1-2 years", 0L);
        brackets.put("2-4 years", 0L);
        brackets.put("4+ years", 0L);
        brackets.put("Unknown", 0L);

        for (Asset a : assets) {
            if (a.getPurchaseDate() == null || a.getPurchaseDate().isBlank()) {
                brackets.merge("Unknown", 1L, Long::sum);
                continue;
            }
            try {
                LocalDate purchased = LocalDate.parse(a.getPurchaseDate());
                long years = java.time.temporal.ChronoUnit.YEARS.between(purchased, today);
                if (years < 1) brackets.merge("< 1 year", 1L, Long::sum);
                else if (years < 2) brackets.merge("1-2 years", 1L, Long::sum);
                else if (years < 4) brackets.merge("2-4 years", 1L, Long::sum);
                else brackets.merge("4+ years", 1L, Long::sum);
            } catch (Exception ex) {
                brackets.merge("Unknown", 1L, Long::sum);
            }
        }
        return brackets;
    }
}
