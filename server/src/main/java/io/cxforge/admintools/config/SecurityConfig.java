package io.cxforge.admintools.config;

import io.cxforge.admintools.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Slf4j
public class SecurityConfig {

    @Value("${security.admin.username:admin}")
    private String adminUsername;

    @Value("${security.admin.password:admin}")
    private String adminPassword;

    @Value("${security.enabled:false}")
    private boolean securityEnabled;

    @Value("${security.oauth2.enabled:false}")
    private boolean oauth2Enabled;

    @Value("${security.oauth2.tenant-claim:tenant_id}")
    private String tenantClaim;

    @Value("${security.oauth2.roles-claim:roles}")
    private String rolesClaim;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/actuator/info",
            "/.well-known/agent-card.json",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/h2-console/**",
            "/"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Configure security headers for all modes
        configureSecurityHeaders(http);

        if (!securityEnabled) {
            // Development mode: disable auth but keep headers
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

        // Production mode: enable full security with HSTS
        http.csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Admin-only endpoints
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/audit/**").hasRole("ADMIN")
                        // All other API endpoints require authentication
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                );

        // Configure authentication method based on settings
        if (oauth2Enabled) {
            log.info("Configuring OAuth2/JWT authentication");
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        } else {
            log.info("Configuring Basic authentication");
            http.httpBasic(basic -> {});
        }

        return http.build();
    }

    private void configureSecurityHeaders(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()) // Allow H2 console
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'self'"
                ))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(permissions -> permissions.policy(
                        "geolocation=(), microphone=(), camera=()"
                ))
        );
    }

    /**
     * JWT Authentication Converter that extracts roles and tenant from JWT claims.
     */
    @Bean
    @ConditionalOnProperty(name = "security.oauth2.enabled", havingValue = "true")
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter());
        return converter;
    }

    /**
     * Custom converter to extract roles from JWT claims and set tenant context.
     */
    private class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        @SuppressWarnings("unchecked")
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            // Extract and set tenant context
            String tenantId = jwt.getClaimAsString(tenantClaim);
            if (tenantId != null) {
                TenantContext.setTenantId(tenantId);
                log.debug("Set tenant context from JWT: {}", tenantId);
            }

            // Extract roles from the configured claim
            Collection<String> roles = extractRoles(jwt);

            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList());
        }

        private Collection<String> extractRoles(Jwt jwt) {
            // Try direct roles claim
            Object rolesObj = jwt.getClaim(rolesClaim);
            if (rolesObj instanceof List<?> rolesList) {
                return rolesList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .collect(Collectors.toList());
            }

            // Try realm_access.roles (Keycloak format)
            Object realmAccess = jwt.getClaim("realm_access");
            if (realmAccess instanceof Map<?, ?> realmMap) {
                Object realmRoles = realmMap.get("roles");
                if (realmRoles instanceof List<?> rolesList) {
                    return rolesList.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .collect(Collectors.toList());
                }
            }

            // Try resource_access.{client_id}.roles (Keycloak client roles)
            Object resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess instanceof Map<?, ?> resourceMap) {
                return resourceMap.values().stream()
                        .filter(Map.class::isInstance)
                        .map(v -> (Map<?, ?>) v)
                        .map(m -> m.get("roles"))
                        .filter(List.class::isInstance)
                        .flatMap(l -> ((List<?>) l).stream())
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .collect(Collectors.toList());
            }

            // Default: no roles
            log.warn("No roles found in JWT token");
            return List.of();
        }
    }

    @Bean
    @ConditionalOnProperty(name = "security.oauth2.enabled", havingValue = "false", matchIfMissing = true)
    public UserDetailsService basicAuthUserDetailsService() {
        var admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder().encode(adminPassword))
                .roles("ADMIN", "USER")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
