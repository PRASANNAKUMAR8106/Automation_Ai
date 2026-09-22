package com.autoflow.modules.influencer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "referral_attributions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_org_attribution", columnNames = {"organization_id"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralAttribution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promo_code_id", nullable = false)
    private PromoCode promoCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "influencer_id", nullable = false)
    private Influencer influencer;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "attribution_type", nullable = false, length = 50)
    private String attributionType; // LINK_CLICK or PROMO_ENTERED

    @CreatedDate
    @Column(name = "attributed_at", nullable = false, updatable = false)
    private Instant attributedAt;
}
