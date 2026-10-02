package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.ConversationIntelligenceDto.*;

import java.util.UUID;

public interface CsatService {

    /**
     * Triggers an automated post-resolution CSAT survey for a resolved conversation,
     * strictly verifying persistent consent (Phase 19) and 24-hour window compliance (Phase 18).
     */
    CsatSurveyResponse triggerPostResolutionSurvey(UUID organizationId, UUID conversationId);

    /**
     * Submits customer feedback and rating for a conversation survey.
     */
    CsatSurveyResponse submitFeedback(UUID organizationId, UUID conversationId, SubmitCsatRequest request);

    /**
     * Retrieves the CSAT survey for a conversation if one exists.
     */
    CsatSurveyResponse getSurvey(UUID organizationId, UUID conversationId);
}
