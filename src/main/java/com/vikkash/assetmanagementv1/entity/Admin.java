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
}
