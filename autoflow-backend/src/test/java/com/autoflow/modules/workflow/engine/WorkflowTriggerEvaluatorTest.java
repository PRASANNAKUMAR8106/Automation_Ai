package com.autoflow.modules.workflow.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Workflow Trigger Evaluator Tests")
class WorkflowTriggerEvaluatorTest {

    private WorkflowTriggerEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new WorkflowTriggerEvaluator();
    }

    @Test
    @DisplayName("Should match comment trigger when comment text contains configured keyword")
    void shouldMatchCommentTriggerContains() {
        DagModel.DagNode triggerNode = DagModel.DagNode.builder()
                .id("trig-1")
                .type("TRIGGER_INSTAGRAM_COMMENT")
                .config(Map.of(
                        "keywords", List.of("GUIDE", "PDF"),
                        "match_type", "CONTAINS",
                        "case_sensitive", false
                ))
                .build();

        InboundEventContext event = InboundEventContext.builder()
                .eventType("COMMENT")
                .commentText("Hey please send me the guide!")
                .build();

        assertTrue(evaluator.matches(triggerNode, event));
    }

    @Test
    @DisplayName("Should reject comment trigger when keyword is missing")
    void shouldRejectCommentTriggerWhenKeywordMissing() {
        DagModel.DagNode triggerNode = DagModel.DagNode.builder()
                .id("trig-1")
                .type("TRIGGER_INSTAGRAM_COMMENT")
                .config(Map.of("keywords", List.of("DISCOUNT")))
                .build();

        InboundEventContext event = InboundEventContext.builder()
                .eventType("COMMENT")
                .commentText("Awesome photo!")
                .build();

        assertFalse(evaluator.matches(triggerNode, event));
    }

    @Test
    @DisplayName("Should support case-sensitive matching")
    void shouldSupportCaseSensitivity() {
        DagModel.DagNode triggerNode = DagModel.DagNode.builder()
                .id("trig-1")
                .type("TRIGGER_INSTAGRAM_COMMENT")
                .config(Map.of(
                        "keywords", List.of("PROMO"),
                        "case_sensitive", true
                ))
                .build();

        InboundEventContext matchingEvent = InboundEventContext.builder()
                .eventType("COMMENT")
                .commentText("I want the PROMO code")
                .build();

        InboundEventContext lowercaseEvent = InboundEventContext.builder()
                .eventType("COMMENT")
                .commentText("I want the promo code")
                .build();

        assertTrue(evaluator.matches(triggerNode, matchingEvent));
        assertFalse(evaluator.matches(triggerNode, lowercaseEvent));
    }

    @Test
    @DisplayName("Should support regex matching")
    void shouldSupportRegexMatching() {
        DagModel.DagNode triggerNode = DagModel.DagNode.builder()
                .id("trig-1")
                .type("TRIGGER_INSTAGRAM_DM")
                .config(Map.of(
                        "keywords", List.of("^order\\s*#?\\d+$"),
                        "match_type", "REGEX"
                ))
                .build();

        InboundEventContext validOrder = InboundEventContext.builder()
                .eventType("DM")
                .messageText("order #12345")
                .build();

        InboundEventContext nonMatching = InboundEventContext.builder()
                .eventType("DM")
                .messageText("where is my package?")
                .build();

        assertTrue(evaluator.matches(triggerNode, validOrder));
        assertFalse(evaluator.matches(triggerNode, nonMatching));
    }

    @Test
    @DisplayName("Should match all comments when keywords list is empty")
    void shouldMatchAllWhenKeywordsEmpty() {
        DagModel.DagNode triggerNode = DagModel.DagNode.builder()
                .id("trig-1")
                .type("TRIGGER_INSTAGRAM_COMMENT")
                .config(Map.of("keywords", List.of()))
                .build();

        InboundEventContext event = InboundEventContext.builder()
                .eventType("COMMENT")
                .commentText("Any arbitrary comment")
                .build();

        assertTrue(evaluator.matches(triggerNode, event));
    }
}
