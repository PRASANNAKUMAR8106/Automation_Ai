package com.autoflow.modules.workflow.repository;

import com.autoflow.modules.workflow.entity.WorkflowVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, UUID> {

    Optional<WorkflowVersion> findByWorkflowIdAndVersionNumber(UUID workflowId, int versionNumber);

    Optional<WorkflowVersion> findTopByWorkflowIdOrderByVersionNumberDesc(UUID workflowId);

    List<WorkflowVersion> findByWorkflowIdOrderByVersionNumberDesc(UUID workflowId);
}
