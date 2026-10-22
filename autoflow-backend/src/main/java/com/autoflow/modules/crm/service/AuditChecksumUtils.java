package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.ConversationInternalNote;
import com.autoflow.modules.crm.entity.ConversationSlaEvent;
import com.autoflow.modules.crm.entity.Message;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/**
 * Canonical serialization and SHA-256 checksums for the conversation audit ledger.
 *
 * <p><b>Canonical form (version 2).</b> The canonical string is a length-prefixed encoding of the
 * audited fields:
 *
 * <pre>
 *   v2:{len}:{value}:{len}:{value}:...      (a null field is encoded as ":-1")
 * </pre>
 *
 * <p>Every field is preceded by its UTF-8 byte length, so the encoding is <em>unambiguous</em>:
 * a value that itself contains the field separator (for example a customer message such as
 * {@code "Order #42: please refund"}) can never be confused with a different field assignment.
 * The previous version 1 joined the fields with a bare {@code ':'}, which meant that the tuple
 * {@code (actor="agent", summary="a:b")} and the tuple {@code (actor="agent:a", summary="b")}
 * produced byte-identical canonical strings - and therefore identical checksums.
 *
 * <p><b>What this guarantees.</b> A stored checksum is a SHA-256 digest over the canonical
 * encoding of the audited fields of a single record. Recomputing that digest from the stored row
 * and comparing it with the stored checksum detects any <em>uncoordinated</em> modification of the
 * audited fields (a direct SQL UPDATE, a buggy write path, a cascade, a partial restore).
 *
 * <p><b>What this does not guarantee.</b> The digest is unkeyed and the checksum column lives in
 * the same table as the data it protects, so an actor with write access to the table can edit a
 * row <em>and</em> re-seal it. It is an integrity tripwire, not a cryptographic proof of
 * immutability. Fields outside the audited set (for example a message's media URL or delivery
 * status) are not covered either.
 */
public final class AuditChecksumUtils {

    public static final String CATEGORY_MESSAGE = "MESSAGE";
    public static final String CATEGORY_INTERNAL_NOTE = "INTERNAL_NOTE";
    public static final String CATEGORY_SLA_EVENT = "SLA_EVENT";

    /**
     * Version marker written as the first element of every canonical string.
     */
    public static final String CANONICAL_VERSION = "v2";

    private AuditChecksumUtils() {}

    /**
     * Computes the canonical SHA-256 checksum of a timeline audit event.
     *
     * <p>Audited fields, in canonical order: organization, conversation, event, category,
     * event type, actor, summary, timestamp (epoch milliseconds).
     */
    public static String computeCanonicalChecksum(UUID orgId, UUID convId, UUID eventId, String category, String eventType, String actor, String summary, Instant ts) {
        StringBuilder canonical = new StringBuilder(CANONICAL_VERSION);
        appendField(canonical, orgId != null ? orgId.toString() : null);
        appendField(canonical, convId != null ? convId.toString() : null);
        appendField(canonical, eventId != null ? eventId.toString() : null);
        // category and event type are controlled vocabularies: normalize them so that a
        // differently-cased value still describes the same audited fact.
        appendField(canonical, category != null ? category.trim().toUpperCase() : null);
        appendField(canonical, eventType != null ? eventType.trim().toUpperCase() : null);
        // actor and summary are free-form and are hashed verbatim: trimming them would let a
        // whitespace-only edit pass verification unnoticed.
        appendField(canonical, actor);
        appendField(canonical, summary);
        appendField(canonical, ts != null ? Long.toString(ts.toEpochMilli()) : null);

        return sha256Hex(canonical.toString());
    }

    /**
     * Timing-safe comparison of two hex checksums.
     */
    public static boolean verifyChecksum(String expectedChecksum, String actualChecksum) {
        if (expectedChecksum == null || actualChecksum == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedChecksum.getBytes(StandardCharsets.UTF_8),
                actualChecksum.getBytes(StandardCharsets.UTF_8)
        );
    }

    // ---------------------------------------------------------------------
    // Entity level helpers.
    //
    // These MUST stay byte-for-byte identical to the checksums produced by
    // ConversationTimelineServiceImpl when it rebuilds a timeline event from the
    // persisted record, otherwise genuine records would be reported as tampered.
    //
    // The tenant and conversation identifiers are passed explicitly so that the
    // sealing path and the verification path always agree on the audited scope;
    // when omitted they fall back to the identifiers carried by the record itself.
    // ---------------------------------------------------------------------

    /**
     * Computes the audit checksum of a message without mutating it.
     */
    public static String computeMessageChecksum(UUID organizationId, UUID conversationId, Message message) {
        if (message == null) {
            return null;
        }
        return computeCanonicalChecksum(
                organizationId != null ? organizationId : message.getOrganizationId(),
                conversationId != null ? conversationId : conversationIdOf(message.getConversation()),
                message.getId(),
                CATEGORY_MESSAGE,
                message.getDirection(),
                message.getSenderType(),
                message.getContent(),
                message.getSentAt()
        );
    }

    /**
     * Computes the audit checksum of an internal note without mutating it.
     */
    public static String computeInternalNoteChecksum(UUID organizationId, UUID conversationId, ConversationInternalNote note) {
        if (note == null) {
            return null;
        }
        return computeCanonicalChecksum(
                organizationId != null ? organizationId : note.getOrganizationId(),
                conversationId != null ? conversationId : conversationIdOf(note.getConversation()),
                note.getId(),
                CATEGORY_INTERNAL_NOTE,
                note.getNoteType(),
                note.getAuthorEmail() != null ? note.getAuthorEmail() : "Team Member",
                note.getContent(),
                note.getCreatedAt()
        );
    }

    /**
     * Computes the audit checksum of an SLA event without mutating it.
     */
    public static String computeSlaEventChecksum(UUID organizationId, UUID conversationId, ConversationSlaEvent event) {
        if (event == null) {
            return null;
        }
        String eventType = event.getEventType() != null ? event.getEventType().name() : "EVENT";
        String summary = event.getReason() != null ? event.getReason() : eventType;
        return computeCanonicalChecksum(
                organizationId != null ? organizationId : event.getOrganizationId(),
                conversationId != null ? conversationId : conversationIdOf(event.getConversation()),
                event.getId(),
                CATEGORY_SLA_EVENT,
                eventType,
                "SYSTEM",
                summary,
                event.getCreatedAt()
        );
    }

    /**
     * Seals a persisted message with its audit checksum.
     * Must be invoked with the managed instance returned by the repository save.
     */
    public static Message sealMessage(UUID organizationId, UUID conversationId, Message message) {
        if (message == null) {
            return null;
        }
        message.setAuditChecksum(computeMessageChecksum(organizationId, conversationId, message));
        return message;
    }

    /**
     * Seals a persisted internal note (or supervisor whisper) with its audit checksum.
     * Must be invoked with the managed instance returned by the repository save.
     */
    public static ConversationInternalNote sealInternalNote(UUID organizationId, UUID conversationId, ConversationInternalNote note) {
        if (note == null) {
            return null;
        }
        note.setAuditChecksum(computeInternalNoteChecksum(organizationId, conversationId, note));
        return note;
    }

    /**
     * Seals a persisted SLA event with its audit checksum.
     * Must be invoked with the managed instance returned by the repository save.
     */
    public static ConversationSlaEvent sealSlaEvent(UUID organizationId, UUID conversationId, ConversationSlaEvent event) {
        if (event == null) {
            return null;
        }
        event.setAuditChecksum(computeSlaEventChecksum(organizationId, conversationId, event));
        return event;
    }

    /**
     * Appends one length-prefixed field to the canonical string.
     *
     * <p>A null field is encoded as {@code ":-1"}; a non-null field as
     * {@code ":{utf8ByteLength}:{value}"}. Because the length is always stated, a reader can
     * recover the exact field boundaries even when a value contains the separator character.
     */
    private static void appendField(StringBuilder canonical, String value) {
        if (value == null) {
            canonical.append(":-1");
            return;
        }
        canonical.append(':').append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
    }

    private static String sha256Hex(String canonicalString) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(canonicalString.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by the platform, so this is unreachable. Fail loudly rather
            // than storing a checksum that could never verify.
            throw new IllegalStateException("SHA-256 is not available on this JVM; audit checksums cannot be computed", e);
        }
    }

    private static UUID conversationIdOf(com.autoflow.modules.crm.entity.Conversation conversation) {
        return conversation != null ? conversation.getId() : null;
    }
}
