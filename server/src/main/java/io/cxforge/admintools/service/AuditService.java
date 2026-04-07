package io.cxforge.admintools.service;

import io.cxforge.admintools.model.AuditLog;
import io.cxforge.admintools.model.AuditLog.AuditAction;
import io.cxforge.admintools.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void logJobCreated(String jobId, String toolId, String details) {
        log(AuditAction.JOB_CREATED, "Job", jobId,
                "Tool: " + toolId + (details != null ? ", " + details : ""), true, null);
    }

    @Async
    public void logJobStarted(String jobId, String toolId) {
        log(AuditAction.JOB_STARTED, "Job", jobId, "Tool: " + toolId, true, null);
    }

    @Async
    public void logJobCompleted(String jobId, int resultsCount) {
        log(AuditAction.JOB_COMPLETED, "Job", jobId, "Results: " + resultsCount, true, null);
    }

    @Async
    public void logJobFailed(String jobId, String errorMessage) {
        log(AuditAction.JOB_FAILED, "Job", jobId, null, false, errorMessage);
    }

    @Async
    public void logJobCancelled(String jobId) {
        log(AuditAction.JOB_CANCELLED, "Job", jobId, "Cancelled by user", true, null);
    }

    @Async
    public void logToolExecuted(String toolId, String parameters) {
        log(AuditAction.TOOL_EXECUTED, "Tool", toolId, parameters, true, null);
    }

    @Async
    public void logLoginSuccess(String username) {
        log(AuditAction.LOGIN_SUCCESS, "User", username, null, true, null);
    }

    @Async
    public void logLoginFailure(String username, String reason) {
        log(AuditAction.LOGIN_FAILURE, "User", username, null, false, reason);
    }

    private void log(AuditAction action, String resourceType, String resourceId,
                     String details, boolean success, String errorMessage) {
        try {
            String username = getCurrentUsername();
            String ipAddress = getClientIpAddress();
            String userAgent = getUserAgent();

            AuditLog auditLog = AuditLog.builder()
                    .timestamp(Instant.now())
                    .username(username)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .details(truncate(details, 2000))
                    .ipAddress(ipAddress)
                    .userAgent(truncate(userAgent, 500))
                    .success(success)
                    .errorMessage(truncate(errorMessage, 500))
                    .build();

            auditLogRepository.save(auditLog);

            if (log.isDebugEnabled()) {
                log.debug("Audit: {} {} {} by {} from {}",
                        action, resourceType, resourceId, username, ipAddress);
            }
        } catch (Exception e) {
            log.error("Failed to write audit log for action: {} resource: {}", action, resourceId, e);
        }
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "anonymous";
    }

    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty()) {
                    ip = request.getRemoteAddr();
                }
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        } catch (Exception e) {
            log.trace("Could not determine client IP", e);
        }
        return "unknown";
    }

    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.trace("Could not determine user agent", e);
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    // Query methods

    public Page<AuditLog> getRecentLogs(int page, int size) {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        return auditLogRepository.findRecentLogs(since, PageRequest.of(page, size));
    }

    public Page<AuditLog> getLogsByUser(String username, Pageable pageable) {
        return auditLogRepository.findByUsernameOrderByTimestampDesc(username, pageable);
    }

    public Page<AuditLog> getLogsByAction(AuditAction action, Pageable pageable) {
        return auditLogRepository.findByActionOrderByTimestampDesc(action, pageable);
    }

    public List<AuditLog> getLogsForResource(String resourceId) {
        return auditLogRepository.findByResourceIdOrderByTimestampDesc(resourceId);
    }

    public Page<AuditLog> getLogsByTimeRange(Instant start, Instant end, Pageable pageable) {
        return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end, pageable);
    }

    // Cleanup old audit logs daily at 2 AM
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldLogs() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        int deleted = auditLogRepository.deleteLogsOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} audit logs older than 90 days", deleted);
        }
    }
}
