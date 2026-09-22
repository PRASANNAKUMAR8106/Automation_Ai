package com.autoflow.modules.workflow.entity;

import com.autoflow.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workflow_version", columnNames = {"workflow_id", "version_number"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "graph_definition", nullable = false, columnDefinition = "jsonb")
    private String graphDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
