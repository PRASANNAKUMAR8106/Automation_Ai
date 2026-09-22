package com.autoflow.modules.influencer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "referral_clicks")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralClick {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promo_code_id", nullable = false)
    private PromoCode promoCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "influencer_id", nullable = false)
    private Influencer influencer;

    @Column(name = "ip_hash", length = 64, nullable = false)
    private String ipHash;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "utm_source", length = 100)
    private String utmSource;

    @Column(name = "utm_medium", length = 100)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 100)
    private String utmCampaign;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
