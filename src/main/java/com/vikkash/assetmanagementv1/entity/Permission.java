package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

/**
 * A single granted capability, e.g. {@code ASSETS_WRITE} or
 * {@code NETWORK_CREDENTIALS_VIEW}. Permissions are grouped under a
 * {@code module} purely for display (the "Manage Roles" screen groups the
 * checkbox list by module) — authorization checks always go by {@code code}.
 *
 * Permissions are seeded once by {@link com.vikkash.assetmanagementv1.config.DataSeeder}
 * and are not expected to be created/edited through the UI — the set of
 * capabilities the application exposes is a code-level concern. What IS
 * editable through the UI is which permissions belong to which {@link Role}.
 */
@Entity
@Table(name = "permission", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable machine key, e.g. "ASSETS_WRITE". Never shown to users directly. */
    @Column(nullable = false, unique = true, length = 60)
    private String code;

    /** Human-readable label for the permissions matrix UI, e.g. "Edit assets". */
    @Column(nullable = false, length = 150)
    private String label;

    /** Grouping used only for display, e.g. "Assets", "Employees", "Network Credentials". */
    @Column(nullable = false, length = 60)
    private String module;

    public Permission() {
    }

    public Permission(String code, String label, String module) {
        this.code = code;
        this.label = label;
        this.module = module;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
}
