package io.cxforge.admintools.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Configuration
@Slf4j
public class SecurityConfigValidator {

    private static final String WEAK_PASSWORD = "admin";
    private static final String EMPTY_PASSWORD = "";

    private final Environment environment;

    @Value("${security.enabled:false}")
    private boolean securityEnabled;

    @Value("${security.admin.password:}")
    private String adminPassword;

    @Value("${aem.enabled:true}")
    private boolean aemEnabled;

    @Value("${aem.password:}")
    private String aemPassword;

    public SecurityConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateConfiguration() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (isProd) {
            validateProductionSecurity();
        } else {
            warnAboutDevelopmentSecurity();
        }
    }

    private void validateProductionSecurity() {
        StringBuilder errors = new StringBuilder();

        if (securityEnabled) {
            if (isWeakPassword(adminPassword)) {
                errors.append("- SECURITY_ADMIN_PASSWORD must be set to a strong password in production\n");
            }
        }

        if (aemEnabled) {
            if (isWeakPassword(aemPassword)) {
                errors.append("- AEM_PASSWORD must be set in production (not 'admin' or empty)\n");
            }
        }

        if (!errors.isEmpty()) {
            String message = """

                ==================== SECURITY CONFIGURATION ERROR ====================
                Production profile is active but security configuration is invalid:

                %s
                Please set these environment variables with secure values.
                ======================================================================
                """.formatted(errors.toString());

            log.error(message);
            throw new IllegalStateException("Invalid security configuration for production environment");
        }

        log.info("Security configuration validated for production environment");
    }

    private void warnAboutDevelopmentSecurity() {
        if (!securityEnabled) {
            log.warn("Security is DISABLED. Enable for production with SECURITY_ENABLED=true");
        }

        if (aemEnabled && isWeakPassword(aemPassword)) {
            log.warn("AEM password is weak or empty. Set AEM_PASSWORD for real AEM connections");
        }

        if (securityEnabled && isWeakPassword(adminPassword)) {
            log.warn("Admin password is weak or empty. Set SECURITY_ADMIN_PASSWORD for secure access");
        }
    }

    private boolean isWeakPassword(String password) {
        return password == null ||
               password.isEmpty() ||
               password.equals(WEAK_PASSWORD) ||
               password.length() < 8;
    }
}
