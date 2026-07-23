package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "admin", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt hash — never store plain text */
    @Column(nullable = false)
    private String password;

    /**
     * Registered recovery email used for Forgot Password OTPs and
     * Network Credential unlock OTPs. Nullable at the DB level so the
     * column can be added to existing deployments without breaking
     * current rows (enforced as required by the service layer instead).
     */
    @Column(unique = true, length = 150)
    private String email;

    // ── Profile (added for role-based login / "load my full profile") ──────
    // All nullable so existing seeded/legacy admin rows keep working
    // unchanged until an admin fills these in via Settings.

    /** Display name shown across the UI — e.g. "Vikkash". */
    @Column(length = 150)
    private String name;

    @Column(unique = true, length = 20)
    private String mobile;

    /** S3 object URL/key for the profile photo, or null if none uploaded. */
    @Column(name = "profile_photo_url", length = 500)
    private String profilePhotoUrl;

    private String department;

    private String designation;

    /** e.g. "Chennai - Kilpauk" — matches the branch values used elsewhere in the app. */
    private String branch;

    // ── Fine-grained authorization ──────────────────────────────────────────
    // The coarse "ADMIN" role (SecurityConfig's hasRole("ADMIN")) is unchanged
    // and still comes from the JWT's "role" claim. This Role is the *within*
    // -ADMIN capability set: which specific modules/actions this particular
    // admin account is authorized for, driving both @PreAuthorize checks on
    // individual endpoints and which sidebar modules the frontend renders.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role roleRef;

    // ── Multi-channel login ─────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /** Google account's stable "sub" claim, set only for Google-provisioned/linked accounts. */
    @Column(name = "google_id", unique = true, length = 100)
    private String googleId;

    public Admin() {
    }

    public Admin(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getProfilePhotoUrl() { return profilePhotoUrl; }
    public void setProfilePhotoUrl(String profilePhotoUrl) { this.profilePhotoUrl = profilePhotoUrl; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public Role getRoleRef() { return roleRef; }
    public void setRoleRef(Role roleRef) { this.roleRef = roleRef; }

    public AuthProvider getAuthProvider() { return authProvider; }
    public void setAuthProvider(AuthProvider authProvider) { this.authProvider = authProvider; }

    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }
}
