package com.vikkash.assetmanagementv1.config;

import com.vikkash.assetmanagementv1.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ── Exception carved out of the wildcard below: GET /api/auth/me
                //    returns the caller's own profile + permissions and must be
                //    authenticated, unlike every other endpoint under /api/auth/**
                //    (login, OTP, password reset) which are necessarily public.
                //    Order matters here — Spring Security uses first-match-wins,
                //    so this narrower rule must come before the wildcard permitAll. ──
                .requestMatchers("/api/auth/me").authenticated()

                // ── Public: login and password change (used during forced first-login) ──
                .requestMatchers("/api/auth/**").permitAll()

                // ── Public: eSSL/ZKTeco biometric device ADMS push protocol
                //    (Attendance Management module). The physical device
                //    firmware calls these URLs directly and cannot perform a
                //    login handshake or send an Authorization header, so this
                //    endpoint is intentionally unauthenticated — the same
                //    trust model as the original standalone eSSL POC. In
                //    production this is expected to be locked down at the
                //    network layer instead (VPN / firewall allow-list to the
                //    device's IP), not by an API key the firmware can't send. ──
                .requestMatchers("/iclock/**").permitAll()

                // ── Admin only ──
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/assets/**").hasRole("ADMIN")
                .requestMatchers("/api/network/**").hasRole("ADMIN")

                // ── Employee self-service + admin can also call these ──
                .requestMatchers("/api/employee/**").hasAnyRole("EMPLOYEE", "ADMIN")

                // ── AI Search Assistant — both roles; results are scoped to the
                //    caller's own assets inside AiSearchService for employees ──
                .requestMatchers("/api/ai/**").hasAnyRole("EMPLOYEE", "ADMIN")

                // ── Everything else requires authentication ──
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "https://haodaasset.vercel.app",
                "https://haodaasset.in",
                "https://www.haodaasset.in"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
