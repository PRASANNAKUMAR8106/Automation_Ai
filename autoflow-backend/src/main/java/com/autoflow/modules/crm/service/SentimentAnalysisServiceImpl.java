package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.ConversationPriority;
import com.autoflow.modules.crm.entity.ConversationSentiment;
import com.autoflow.modules.crm.entity.LeadStatus;
import com.autoflow.modules.crm.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentAnalysisServiceImpl implements SentimentAnalysisService {

    private final ContactRepository contactRepository;

    private static final Set<String> CHURN_KEYWORDS = Set.of(
            "cancel subscription", "cancel my", "refund immediately", "scam",
            "fraud", "lawyer", "attorney", "chargeback", "dispute",
            "unacceptable", "terrible", "worst service", "sue", "legal action"
    );

    private static final Set<String> POSITIVE_KEYWORDS = Set.of(
            "love", "great", "awesome", "fantastic", "amazing", "thank",
            "thanks", "excellent", "good", "perfect", "helpful", "appreciate",
            "superb", "brilliant", "delighted"
    );

    private static final Set<String> NEGATIVE_KEYWORDS = Set.of(
            "broken", "issue", "bug", "error", "failed", "not working",
            "slow", "problem", "disappointed", "complaint", "bad", "frustrated"
    );

    private static final Set<String> HIGH_INTENT_KEYWORDS = Set.of(
            "buy", "pricing", "price", "cost", "upgrade", "demo", "quote",
            "interested", "purchase", "starter", "pro plan", "enterprise"
    );

    private static final Set<String> URGENT_KEYWORDS = Set.of(
            "emergency", "asap", "urgent", "immediately", "critical", "down"
    );

    @Override
    public ConversationSentiment analyzeSentiment(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return ConversationSentiment.NEUTRAL;
        }

        String lower = messageText.toLowerCase(Locale.ROOT);

        for (String churn : CHURN_KEYWORDS) {
            if (lower.contains(churn)) {
                return ConversationSentiment.CHURN_RISK;
            }
        }

        for (String neg : NEGATIVE_KEYWORDS) {
            if (lower.contains(neg)) {
                return ConversationSentiment.NEGATIVE;
            }
        }

        for (String pos : POSITIVE_KEYWORDS) {
            if (lower.contains(pos)) {
                return ConversationSentiment.POSITIVE;
            }
        }

        return ConversationSentiment.NEUTRAL;
    }

    @Override
    public ConversationPriority determinePriority(String messageText, ConversationSentiment sentiment) {
        if (sentiment == ConversationSentiment.CHURN_RISK) {
            return ConversationPriority.URGENT;
        }

        if (messageText != null) {
            String lower = messageText.toLowerCase(Locale.ROOT);
            for (String urgent : URGENT_KEYWORDS) {
                if (lower.contains(urgent)) {
                    return ConversationPriority.URGENT;
                }
            }
            for (String intent : HIGH_INTENT_KEYWORDS) {
                if (lower.contains(intent)) {
                    return ConversationPriority.HIGH;
                }
            }
        }

        if (sentiment == ConversationSentiment.NEGATIVE) {
            return ConversationPriority.HIGH;
        }

        return ConversationPriority.NORMAL;
    }

    @Override
    public void processInboundIntelligence(Conversation conversation, Contact contact, String messageText) {
        ConversationSentiment sentiment = analyzeSentiment(messageText);
        ConversationPriority priority = determinePriority(messageText, sentiment);

        conversation.setSentiment(sentiment);
        // Only increase priority or set if higher than current
        if (priority.ordinal() > conversation.getPriority().ordinal()) {
            conversation.setPriority(priority);
        }

        if (contact != null) {
            int scoreDelta = 0;
            switch (sentiment) {
                case POSITIVE -> scoreDelta += 10;
                case NEGATIVE -> scoreDelta -= 5;
                case CHURN_RISK -> scoreDelta -= 20;
                default -> {}
            }

            if (messageText != null) {
                String lower = messageText.toLowerCase(Locale.ROOT);
                for (String intent : HIGH_INTENT_KEYWORDS) {
                    if (lower.contains(intent)) {
                        scoreDelta += 15;
                        break;
                    }
                }
            }

            if (scoreDelta != 0) {
                int newScore = Math.max(0, contact.getLeadScore() + scoreDelta);
                contact.setLeadScore(newScore);

                // Auto-qualify leads with high intent and high engagement score
                if (newScore >= 50 && contact.getLeadStatus() == LeadStatus.NEW) {
                    contact.setLeadStatus(LeadStatus.QUALIFIED);
                    log.info("Contact [{}] automatically advanced to QUALIFIED status (Score: {})", contact.getId(), newScore);
                }
                contactRepository.save(contact);
            }
        }
    }
}
