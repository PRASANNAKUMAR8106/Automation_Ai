package com.autoflow.modules.crm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audit ledger relies on the canonical serialization being <em>unambiguous</em>: a checksum
 * must be bound to exactly one field tuple, otherwise two different records can share a digest
 * and a verification pass proves nothing.
 */
@DisplayName("Audit Checksum Canonicalization Tests")
class AuditChecksumUtilsTest {

    private final UUID orgId = UUID.randomUUID();
    private final UUID convId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final Instant ts = Instant.parse("2026-10-22T09:00:00Z");

    @Test
    @DisplayName("Should produce a stable 64 character hex digest for the same inputs")
    void shouldBeDeterministic() {
        String first = AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "CONTACT", "Hello", ts);
        String second = AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "CONTACT", "Hello", ts);

        assertThat(first).hasSize(64).isEqualTo(second);
    }

    @Test
    @DisplayName("Should not let a separator inside a free-form field collide with a different field assignment")
    void shouldBeUnambiguousForSeparatorBearingValues() {
        // Under a bare ':' joined encoding these two tuples produced byte-identical canonical
        // strings, and therefore the same digest.
        String shifted = AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "agent:a", "b", ts);
        String direct = AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "agent", "a:b", ts);

        assertThat(shifted).isNotEqualTo(direct);
    }

    @Test
    @DisplayName("Should distinguish a null field from an empty field")
    void shouldDistinguishNullFromEmpty() {
        String nullActor = AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", null, "body", ts);
        String emptyActor = AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "", "body", ts);

        assertThat(nullActor).isNotEqualTo(emptyActor);
    }

    @Test
    @DisplayName("Should detect a whitespace-only change in the summary")
    void shouldDetectWhitespaceOnlyChange() {
        String original = AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "CONTACT", "Hello", ts);
        String padded = AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "CONTACT", "Hello ", ts);

        assertThat(original).isNotEqualTo(padded);
    }

    @Test
    @DisplayName("Should detect a change in any single audited field")
    void shouldDetectChangeInEachAuditedField() {
        String base = AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "CONTACT", "Hello", ts);

        assertThat(AuditChecksumUtils.computeCanonicalChecksum(UUID.randomUUID(), convId, eventId, "MESSAGE", "INBOUND", "CONTACT", "Hello", ts)).isNotEqualTo(base);
        assertThat(AuditChecksumUtils.computeCanonicalChecksum(orgId, UUID.randomUUID(), eventId, "MESSAGE", "INBOUND", "CONTACT", "Hello", ts)).isNotEqualTo(base);
        assertThat(AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, UUID.randomUUID(), "MESSAGE", "INBOUND", "CONTACT", "Hello", ts)).isNotEqualTo(base);
        assertThat(AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "INTERNAL_NOTE", "INBOUND", "CONTACT", "Hello", ts)).isNotEqualTo(base);
        assertThat(AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "OUTBOUND", "CONTACT", "Hello", ts)).isNotEqualTo(base);
        assertThat(AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "AGENT", "Hello", ts)).isNotEqualTo(base);
        assertThat(AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "CONTACT", "Hello!", ts)).isNotEqualTo(base);
        assertThat(AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "CONTACT", "Hello", ts.plusMillis(1))).isNotEqualTo(base);
    }

    @Test
    @DisplayName("Should treat the controlled category and event type vocabularies as case insensitive")
    void shouldNormalizeControlledVocabularies() {
        String upper = AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "CONTACT", "Hello", ts);
        String lower = AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "message", "inbound", "CONTACT", "Hello", ts);

        assertThat(upper).isEqualTo(lower);
    }

    @Test
    @DisplayName("Should verify a matching checksum and reject a mismatching one")
    void shouldVerifyChecksums() {
        String checksum = AuditChecksumUtils.computeCanonicalChecksum(orgId, convId, eventId, "MESSAGE", "INBOUND", "CONTACT", "Hello", ts);

        assertThat(AuditChecksumUtils.verifyChecksum(checksum, checksum)).isTrue();
        assertThat(AuditChecksumUtils.verifyChecksum(checksum, "0".repeat(64))).isFalse();
        assertThat(AuditChecksumUtils.verifyChecksum(null, checksum)).isFalse();
        assertThat(AuditChecksumUtils.verifyChecksum(checksum, null)).isFalse();
    }

    @Test
    @DisplayName("Should use the versioned canonical prefix")
    void shouldUseVersionedCanonicalPrefix() {
        assertThat(AuditChecksumUtils.CANONICAL_VERSION).isEqualTo("v2");
    }
}
