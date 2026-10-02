package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.entity.CrmMacro;

import java.util.List;
import java.util.UUID;

public interface MacroExecutionService {

    CrmMacro createMacro(UUID organizationId, UUID userId, CrmMacroRequest request);

    CrmMacro updateMacro(UUID organizationId, UUID id, CrmMacroRequest request);

    void deleteMacro(UUID organizationId, UUID id);

    List<CrmMacro> getMacros(UUID organizationId);

    ApplyMacroResult applyMacro(UUID organizationId, UUID conversationId, UUID macroId, UUID executingUserId, String agentName);
}
