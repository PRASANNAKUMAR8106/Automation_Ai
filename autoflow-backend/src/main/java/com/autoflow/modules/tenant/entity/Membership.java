package com.autoflow.modules.tenant.entity;

import com.autoflow.common.BaseEntity;
import com.autoflow.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "memberships", uniqueConstraints = {
        @UniqueConstraint(name = "uk_memberships_org_user", columnNames = {"organization_id", "user_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Membership extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private MembershipRole role = MembershipRole.VIEWER;
}
