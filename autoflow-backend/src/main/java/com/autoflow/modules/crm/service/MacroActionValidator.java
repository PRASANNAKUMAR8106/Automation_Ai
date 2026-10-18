package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.ConversationPriority;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Strict schema validator for macro actions_json to guarantee that no arbitrary,
 * unvalidated, or injection-prone commands are executed.
 */
public class MacroActionValidator {

    private static final Set<String> ALLOWED_ACTION_TYPES = Set.of(
            "SEND_REPLY",
            "ADD_TAGS",
            "REMOVE_TAGS",
            "SET_PRIORITY",
            "ASSIGN_AGENT",
            "RESOLVE"
    );

    public static void validate(String actionsJson, ObjectMapper objectMapper) {
        if (actionsJson == null || actionsJson.isBlank()) {
            throw new IllegalArgumentException("Macro actionsJson cannot be null or empty");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(actionsJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Macro actionsJson is not valid JSON: " + e.getMessage());
        }

        if (!root.isArray()) {
            throw new IllegalArgumentException("Macro actionsJson must be a JSON array of action objects");
        }

        if (root.isEmpty() || root.size() > 20) {
            throw new IllegalArgumentException("Macro actionsJson must contain between 1 and 20 actions");
        }

        for (int i = 0; i < root.size(); i++) {
            JsonNode node = root.get(i);
            if (!node.isObject()) {
                throw new IllegalArgumentException("Action at index " + i + " must be a JSON object");
            }

            JsonNode typeNode = node.get("type");
            if (typeNode == null || !typeNode.isTextual() || typeNode.asText().isBlank()) {
                throw new IllegalArgumentException("Action at index " + i + " must contain a non-blank 'type' string");
            }

            String type = typeNode.asText().trim().toUpperCase();
            if (!ALLOWED_ACTION_TYPES.contains(type)) {
                throw new IllegalArgumentException("Unsupported macro action type '" + type + "' at index " + i +
                        ". Allowed types: " + ALLOWED_ACTION_TYPES);
            }

            validateActionFields(i, type, node);
        }
    }

    private static void validateActionFields(int index, String type, JsonNode node) {
        switch (type) {
            case "SEND_REPLY" -> {
                assertOnlyAllowedKeys(index, node, Set.of("type", "content"));
                JsonNode content = node.get("content");
                if (content == null || !content.isTextual() || content.asText().isBlank()) {
                    throw new IllegalArgumentException("SEND_REPLY action at index " + index + " requires a non-blank 'content' string");
                }
                if (content.asText().length() > 4096) {
                    throw new IllegalArgumentException("SEND_REPLY content at index " + index + " exceeds maximum allowed length of 4096 characters");
                }
            }
            case "ADD_TAGS", "REMOVE_TAGS" -> {
                assertOnlyAllowedKeys(index, node, Set.of("type", "tags"));
                JsonNode tags = node.get("tags");
                if (tags == null || (!tags.isArray() && !tags.isTextual())) {
                    throw new IllegalArgumentException(type + " action at index " + index + " requires a 'tags' array or comma-separated string");
                }
                if (tags.isArray()) {
                    if (tags.isEmpty() || tags.size() > 20) {
                        throw new IllegalArgumentException(type + " tags array at index " + index + " must contain between 1 and 20 items");
                    }
                    for (JsonNode t : tags) {
                        if (!t.isTextual() || t.asText().isBlank() || t.asText().length() > 50) {
                            throw new IllegalArgumentException(type + " contains invalid tag at index " + index + ": must be non-blank text up to 50 chars");
                        }
                    }
                }
            }
            case "SET_PRIORITY" -> {
                assertOnlyAllowedKeys(index, node, Set.of("type", "priority"));
                JsonNode priority = node.get("priority");
                if (priority == null || !priority.isTextual()) {
                    throw new IllegalArgumentException("SET_PRIORITY action at index " + index + " requires a 'priority' string");
                }
                try {
                    ConversationPriority.valueOf(priority.asText().trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid priority value '" + priority.asText() + "' at index " + index +
                            ". Allowed values: LOW, NORMAL, HIGH, URGENT");
                }
            }
            case "ASSIGN_AGENT" -> {
                assertOnlyAllowedKeys(index, node, Set.of("type", "channelSpecialist", "userId"));
                boolean hasSpecialist = node.has("channelSpecialist") && node.get("channelSpecialist").isBoolean();
                boolean hasUserId = node.hasNonNull("userId") && node.get("userId").isTextual();
                if (!hasSpecialist && !hasUserId) {
                    throw new IllegalArgumentException("ASSIGN_AGENT action at index " + index +
                            " must specify either 'channelSpecialist' (boolean) or 'userId' (valid UUID string)");
                }
                if (hasUserId) {
                    try {
                        UUID.fromString(node.get("userId").asText().trim());
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Invalid UUID format for 'userId' in ASSIGN_AGENT at index " + index);
                    }
                }
            }
            case "RESOLVE" -> {
                assertOnlyAllowedKeys(index, node, Set.of("type", "resolved"));
                JsonNode resolved = node.get("resolved");
                if (resolved != null && !resolved.isBoolean()) {
                    throw new IllegalArgumentException("RESOLVE action at index " + index + " 'resolved' field must be a boolean");
                }
            }
        }
    }

    private static void assertOnlyAllowedKeys(int index, JsonNode node, Set<String> allowedKeys) {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!allowedKeys.contains(field)) {
                throw new IllegalArgumentException("Unrecognized field '" + field + "' in macro action at index " + index +
                        ". Allowed fields for this action: " + allowedKeys);
            }
        }
    }
}
