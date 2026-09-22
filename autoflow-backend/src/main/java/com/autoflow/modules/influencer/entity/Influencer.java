package com.autoflow.modules.influencer.entity;

import com.autoflow.common.BaseEntity;
import com.autoflow.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "influencers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Influencer extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "instagram_handle", length = 100)
    private String instagramHandle;

    @Column(name = "country", length = 100)
    @Builder.Default
    private String country = "IN";

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "encrypted_payout_details", columnDefinition = "text")
    private String encryptedPayoutDetails;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
