package com.autoflow.modules.billing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "plan_features", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plan_feature", columnNames = {"plan_id", "feature_key"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "feature_key", nullable = false, length = 100)
    private String featureKey;

    @Column(name = "limit_value", nullable = false)
    @Builder.Default
    private Long limitValue = 0L;

    @Column(name = "is_unlimited", nullable = false)
    @Builder.Default
    private boolean unlimited = false;
}
