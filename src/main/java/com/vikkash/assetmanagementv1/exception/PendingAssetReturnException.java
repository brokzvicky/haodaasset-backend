package com.vikkash.assetmanagementv1.exception;

import com.vikkash.assetmanagementv1.entity.Asset;

import java.util.List;

/**
 * Thrown when an admin tries to complete an employee's resignation (mark
 * them "Resigned") while one or more assets are still assigned to them.
 * Carries the exact list of pending assets so the frontend can render a
 * clear, actionable warning instead of a generic error string.
 */
public class PendingAssetReturnException extends RuntimeException {

    private final List<Asset> pendingAssets;

    public PendingAssetReturnException(List<Asset> pendingAssets) {
        super("This employee still has " + pendingAssets.size()
                + " asset(s) assigned. All assets must be returned before resignation can be completed.");
        this.pendingAssets = pendingAssets;
    }

    public List<Asset> getPendingAssets() {
        return pendingAssets;
    }
}
