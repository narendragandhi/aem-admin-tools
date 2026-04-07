package io.cxforge.admintools.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_user", columnList = "username"),
        @Index(name = "idx_audit_action", columnList = "action")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(nullable = false)
    private String resourceType;

    private String resourceId;

    @Column(length = 2000)
    private String details;

    private String ipAddress;

    private String userAgent;

    private boolean success;

    private String errorMessage;

    public enum AuditAction {
        JOB_CREATED,
        JOB_STARTED,
        JOB_COMPLETED,
        JOB_FAILED,
        JOB_CANCELLED,
        TOOL_EXECUTED,
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        CONFIG_CHANGED,
        EXPORT_DATA
    }
}
