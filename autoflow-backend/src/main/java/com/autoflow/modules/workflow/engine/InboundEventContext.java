package com.autoflow.modules.workflow.engine;

import com.autoflow.modules.crm.entity.ChannelType;
import lombok.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundEventContext {

    private UUID organizationId;
    private ChannelType channel;
    private String eventType; // COMMENT, DM, MENTION
    private String externalAccountId; // IG Business Account ID
    private String contactExternalId; // Sender user ID
    private String username;
    private String fullName;
    private String commentId;
    private String commentText;
    private String messageId;
    private String messageText;
    private String pageId;
    private String pageAccessToken;

    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
}
