package com.autoflow.modules.workflow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "automation_executions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_version_id")
    private WorkflowVersion workflowVersion;

    @Column(name = "trigger_type", nullable = false, length = 100)
    private String triggerType;

    @Column(name = "trigger_event_id")
    private String triggerEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.RUNNING;

    @Column(name = "current_node_id", length = 100)
    private String currentNodeId;

    @Column(name = "execution_context", columnDefinition = "jsonb")
    @Builder.Default
    private String executionContext = "{}";

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @CreatedDate
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
