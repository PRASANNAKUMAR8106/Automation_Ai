package com.autoflow.modules.auth.dto;

import com.autoflow.modules.tenant.entity.MembershipRole;
import com.autoflow.modules.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private boolean emailVerified;
    private UUID activeOrganizationId;
    private String activeOrganizationName;
    private MembershipRole activeMembershipRole;
}
