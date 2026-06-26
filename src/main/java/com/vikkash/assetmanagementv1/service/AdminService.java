package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.AdminLoginRequest;
import com.vikkash.assetmanagementv1.dto.LoginResponse;
import com.vikkash.assetmanagementv1.entity.Admin;
import com.vikkash.assetmanagementv1.exception.InvalidCredentialsException;
import com.vikkash.assetmanagementv1.repository.AdminRepository;
import com.vikkash.assetmanagementv1.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AdminService(AdminRepository adminRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(AdminLoginRequest request) {
        Admin admin = adminRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(admin.getUsername(), "ADMIN");
        return LoginResponse.forAdmin(token, admin.getUsername());
    }
}
