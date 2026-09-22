package com.autoflow.modules.billing.entity;

import com.autoflow.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "price_inr", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal priceInr = BigDecimal.ZERO;

    @Column(name = "billing_interval", nullable = false, length = 20)
    @Builder.Default
    private String billingInterval = "MONTHLY";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<PlanFeature> features = new ArrayList<>();
}
