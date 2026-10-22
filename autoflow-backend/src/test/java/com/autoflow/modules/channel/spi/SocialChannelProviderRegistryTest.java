package com.autoflow.modules.channel.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The outbound firewall is the last line of defence that keeps internal notes and supervisor
 * whispers from ever reaching a customer's social channel. It is applied on every dispatch path,
 * so it is verified here as a single shared policy.
 */
@DisplayName("Social Channel Outbound Firewall Tests")
class SocialChannelProviderRegistryTest {

    @Test
    @DisplayName("Should allow a normal outbound agent reply")
    void shouldAllowNormalOutboundMessage() {
        assertThatCode(() -> SocialChannelProviderRegistry.assertOutboundPermitted(
                "TEXT", "AGENT", "Hi, how can I help you today?", null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should block an internal note message type")
    void shouldBlockInternalNoteMessageType() {
        assertThatThrownBy(() -> SocialChannelProviderRegistry.assertOutboundPermitted(
                "INTERNAL_NOTE", "AGENT", "Refund approved internally", null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("FIREWALL BREACH");
    }

    @Test
    @DisplayName("Should block a supervisor whisper message type")
    void shouldBlockSupervisorWhisperMessageType() {
        assertThatThrownBy(() -> SocialChannelProviderRegistry.assertOutboundPermitted(
                "SUPERVISOR_WHISPER", "AGENT", "Escalate this", null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("FIREWALL BREACH");
    }

    @Test
    @DisplayName("Should block an internal sender type")
    void shouldBlockInternalSenderType() {
        assertThatThrownBy(() -> SocialChannelProviderRegistry.assertOutboundPermitted(
                "TEXT", "INTERNAL", "Refund approved internally", null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("FIREWALL BREACH");
    }

    @Test
    @DisplayName("Should block content tagged as an internal note")
    void shouldBlockTaggedContent() {
        assertThatThrownBy(() -> SocialChannelProviderRegistry.assertOutboundPermitted(
                "TEXT", "AGENT", "[INTERNAL_NOTE] refund approved", null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("FIREWALL BREACH");
    }

    @Test
    @DisplayName("Should block content tagged as a supervisor whisper")
    void shouldBlockTaggedWhisperContent() {
        assertThatThrownBy(() -> SocialChannelProviderRegistry.assertOutboundPermitted(
                "TEXT", "AGENT", "[SUPERVISOR_WHISPER] escalate this", null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("FIREWALL BREACH");
    }

    @Test
    @DisplayName("Should block media tagged as an internal note")
    void shouldBlockTaggedMedia() {
        assertThatThrownBy(() -> SocialChannelProviderRegistry.assertOutboundPermitted(
                "MEDIA", "AGENT", "here is the document", "https://s3.aws.com/bucket/[INTERNAL_NOTE]/doc.pdf"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("FIREWALL BREACH");
    }
}
