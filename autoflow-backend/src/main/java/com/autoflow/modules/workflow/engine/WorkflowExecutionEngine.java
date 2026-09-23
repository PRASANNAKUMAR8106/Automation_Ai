package com.autoflow.modules.workflow.engine;

import com.autoflow.modules.workflow.entity.AutomationExecution;
import com.autoflow.modules.workflow.entity.Workflow;
import com.autoflow.modules.workflow.entity.WorkflowVersion;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface WorkflowExecutionEngine {

    /**
     * Evaluates all published workflows for the event's organization and executes matching workflows.
     */
    void evaluateAndExecute(InboundEventContext event);

    /**
     * Executes a specific workflow version with the given event context.
     */
    AutomationExecution executeWorkflow(Workflow workflow, WorkflowVersion version, InboundEventContext event);

    /**
     * Re-dispatches a previously failed execution for retry on the virtual executor.
     * Transitions status to RUNNING, executes DAG nodes, updates telemetry,
     * and sets terminal status to SUCCESS or FAILED.
     */
    CompletableFuture<AutomationExecution> reDispatchExecution(UUID executionId);
}

