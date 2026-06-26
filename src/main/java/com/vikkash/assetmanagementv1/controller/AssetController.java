package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.AssignAssetRequest;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for asset inventory management.
 * Requires ROLE_ADMIN (enforced in SecurityConfig for /assets/**).
 * All business logic is delegated to AssetService.
 */
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public List<Asset> getAllAssets() {
        return assetService.getAllAssets();
    }

    @GetMapping("/available")
    public List<Asset> getAvailableAssets() {
        return assetService.getAvailableAssets();
    }

    @GetMapping("/dashboard")
    public Map<String, Long> dashboard() {
        return assetService.getDashboardStats();
    }

    @GetMapping("/employee/{name}")
    public List<Asset> getAssetsByEmployee(@PathVariable String name) {
        return assetService.getByEmployee(name);
    }

    @GetMapping("/serial/{serialNumber}")
    public Asset getAssetBySerialNumber(@PathVariable String serialNumber) {
        return assetService.getBySerialNumber(serialNumber);
    }

    @PostMapping
    public ResponseEntity<Asset> saveAsset(@RequestBody Asset asset) {
        return ResponseEntity.status(201).body(assetService.createAsset(asset));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Asset> updateAsset(@PathVariable Long id, @RequestBody Asset updatedAsset) {
        return ResponseEntity.ok(assetService.updateAsset(id, updatedAsset));
    }

    @PutMapping("/assign/{id}")
    public ResponseEntity<Asset> assignAsset(@PathVariable Long id,
                                              @Valid @RequestBody AssignAssetRequest request) {
        return ResponseEntity.ok(assetService.assignAsset(id, request));
    }

    @PutMapping("/return/{id}")
    public ResponseEntity<Asset> returnAsset(@PathVariable Long id,
                                              @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(assetService.returnAsset(id, body));
    }

    @PutMapping("/relieve/{id}")
    public ResponseEntity<Asset> relieveEmployee(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.relieveEmployee(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return ResponseEntity.ok(Map.of("message", "Asset deleted successfully"));
    }
}
