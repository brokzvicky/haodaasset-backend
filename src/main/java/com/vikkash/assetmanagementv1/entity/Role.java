package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/**
 * A named bundle of {@link Permission}s — e.g. "System Admin", "Asset
 * Manager", "HR Admin", "Support Engineer", "Employee". Both {@link Admin}
 * and {@link Employee} rows point at a Role via {@code role_id}; the
 * authenticated user's effective permission set is simply
 * {@code role.getPermissions()}, embedded into their JWT at login time so
 * every request (and the frontend's module visibility) can check it without
 * a DB round-trip.
 *
 * This is deliberately additive to (not a replacement for) the existing
 * coarse {@code Admin}/{@code Employee.role} String ("ADMIN"/"EMPLOYEE")
 * that {@link com.vikkash.assetmanagementv1.config.SecurityConfig} already
 * gates whole API prefixes on — that coarse split stays exactly as-is.
 * This Role is the finer-grained layer on top of it: which specific
 * modules/actions within "ADMIN" a given admin actually gets.
 */
@Entity
@Table(name = "role", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "SYSTEM_ADMIN", "ASSET_MANAGER", "HR_ADMIN", "SUPPORT_ENGINEER", "EMPLOYEE" */
    @Column(nullable = false, unique = true, length = 60)
    private String name;

    /** Display label shown in the UI, e.g. "System Admin". */
    @Column(nullable = false, length = 100)
    private String label;

    @Column(length = 300)
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    public Role() {
    }

    public Role(String name, String label, String description) {
        this.name = name;
        this.label = label;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Set<Permission> getPermissions() { return permissions; }
    public void setPermissions(Set<Permission> permissions) { this.permissions = permissions; }
}
