package com.autoflow.modules.workflow.repository;

import com.autoflow.modules.workflow.entity.AutomationExecution;
import com.autoflow.modules.workflow.entity.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, UUID> {

    List<AutomationExecution> findByOrganizationIdOrderByStartedAtDesc(UUID organizationId);

    List<AutomationExecution> findByWorkflowIdOrderByStartedAtDesc(UUID workflowId);

    long countByOrganizationIdAndStatus(UUID organizationId, ExecutionStatus status);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndTriggerType(UUID organizationId, String triggerType);
}
