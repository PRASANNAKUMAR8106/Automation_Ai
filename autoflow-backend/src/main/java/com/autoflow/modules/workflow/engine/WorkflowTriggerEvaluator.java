package com.autoflow.modules.workflow.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
public class WorkflowTriggerEvaluator {

    public boolean matches(DagModel.DagNode triggerNode, InboundEventContext event) {
        if (triggerNode == null || triggerNode.getType() == null) {
            return false;
        }

        String type = triggerNode.getType();
        Map<String, Object> config = triggerNode.getConfig() != null ? triggerNode.getConfig() : Map.of();

        return switch (type) {
            case "TRIGGER_INSTAGRAM_COMMENT" ->
                    "COMMENT".equalsIgnoreCase(event.getEventType()) && matchesKeyword(event.getCommentText(), config);
            case "TRIGGER_INSTAGRAM_DM" ->
                    "DM".equalsIgnoreCase(event.getEventType()) && matchesKeyword(event.getMessageText(), config);
            case "TRIGGER_STORY_MENTION" ->
                    "MENTION".equalsIgnoreCase(event.getEventType());
            case "TRIGGER_KEYWORD" -> {
                String text = event.getCommentText() != null ? event.getCommentText() : event.getMessageText();
                yield matchesKeyword(text, config);
            }
            default -> false;
        };
    }

    @SuppressWarnings("unchecked")
    private boolean matchesKeyword(String text, Map<String, Object> config) {
        if (text == null) return false;

        Object keywordsObj = config.get("keywords");
        List<String> keywords = null;
        if (keywordsObj instanceof Collection<?>) {
            keywords = ((Collection<?>) keywordsObj).stream()
                    .map(Object::toString)
                    .toList();
        } else if (keywordsObj instanceof String s) {
            keywords = List.of(s);
        }

        // If no keywords specified, matches any incoming message/comment
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }

        boolean caseSensitive = Boolean.TRUE.equals(config.get("case_sensitive"));
        String matchType = config.getOrDefault("match_type", "CONTAINS").toString().toUpperCase();

        String targetText = caseSensitive ? text : text.toLowerCase();

        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) continue;
            String searchKw = caseSensitive ? keyword.trim() : keyword.trim().toLowerCase();

            switch (matchType) {
                case "EXACT" -> {
                    if (targetText.trim().equals(searchKw)) return true;
                }
                case "REGEX" -> {
                    try {
                        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                        if (Pattern.compile(keyword, flags).matcher(text).find()) return true;
                    } catch (Exception e) {
                        log.warn("Invalid regex pattern in trigger config: {}", keyword);
                    }
                }
                case "CONTAINS" -> {
                    if (targetText.contains(searchKw)) return true;
                }
                default -> {
                    if (targetText.contains(searchKw)) return true;
                }
            }
        }

        return false;
    }
}
