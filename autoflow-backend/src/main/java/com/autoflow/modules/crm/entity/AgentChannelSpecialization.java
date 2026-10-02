package com.autoflow.modules.crm.entity;

import com.autoflow.common.TenantAwareEntity;
import com.autoflow.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agent_channel_specializations", uniqueConstraints = {
        @UniqueConstraint(name = "uq_agent_channel_specialization", columnNames = {"organization_id", "user_id", "channel"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChannelSpecialization extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ChannelType channel;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
