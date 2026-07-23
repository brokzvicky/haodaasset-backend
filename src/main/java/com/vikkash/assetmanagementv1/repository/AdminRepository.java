package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<Admin> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    /** Used by the "Login with Mobile" flow. */
    Optional<Admin> findByMobile(String mobile);

    /** Used by Google Sign-In to find an account already linked to this Google identity. */
    Optional<Admin> findByGoogleId(String googleId);
}
