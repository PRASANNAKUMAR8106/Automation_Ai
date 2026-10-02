package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.ConversationPriority;
import com.autoflow.modules.crm.entity.ConversationSentiment;

public interface SentimentAnalysisService {

    /**
     * Evaluates incoming message content and determines sentiment classification.
     */
    ConversationSentiment analyzeSentiment(String messageText);

    /**
     * Determines the suggested priority of the conversation based on message urgency and sentiment.
     */
    ConversationPriority determinePriority(String messageText, ConversationSentiment sentiment);

    /**
     * Processes inbound message text to update conversation sentiment, priority,
     * and advances contact lead score/status.
     */
    void processInboundIntelligence(Conversation conversation, Contact contact, String messageText);
}
