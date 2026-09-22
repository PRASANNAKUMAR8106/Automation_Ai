package com.autoflow.modules;

import com.autoflow.common.Money;
import com.autoflow.modules.billing.entity.*;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.influencer.entity.*;
import com.autoflow.modules.tenant.entity.*;
import com.autoflow.modules.user.entity.*;
import com.autoflow.modules.workflow.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JPA Schema Entities Model Verification Tests")
class SchemaEntitiesTest {

    @Test
    @DisplayName("Should instantiate Organization and Membership with proper relationships")
    void testTenantAndMembership() {
        UUID orgId = UUID.randomUUID();
        Organization org = Organization.builder()
                .name("Acme Agency")
                .slug("acme-agency")
                .build();
        org.setId(orgId);

        User user = User.builder()
                .email("alex@acme.com")
                .passwordHash("$2a$12$securehash")
                .firstName("Alex")
                .lastName("Rivera")
                .role(Role.CREATOR)
                .build();

        Membership membership = Membership.builder()
                .organizationId(orgId)
                .user(user)
                .role(MembershipRole.OWNER)
                .build();

        assertThat(org.getSlug()).isEqualTo("acme-agency");
        assertThat(membership.getRole()).isEqualTo(MembershipRole.OWNER);
        assertThat(membership.getUser().getEmail()).isEqualTo("alex@acme.com");
    }

    @Test
    @DisplayName("Should validate Subscription operability and grace period logic")
    void testSubscriptionGracePeriod() {
        Plan plan = Plan.builder()
                .code("PRO")
                .name("Pro Marketer")
                .priceInr(new BigDecimal("1499.00"))
                .build();

        Subscription subActive = Subscription.builder()
                .organizationId(UUID.randomUUID())
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(Instant.now().minusSeconds(86400))
                .currentPeriodEnd(Instant.now().plusSeconds(86400 * 29))
                .build();
        assertThat(subActive.isOperable()).isTrue();

        // Past due with grace period in future
        Subscription subGrace = Subscription.builder()
                .organizationId(UUID.randomUUID())
                .plan(plan)
                .status(SubscriptionStatus.PAST_DUE)
                .currentPeriodStart(Instant.now().minusSeconds(86400 * 30))
                .currentPeriodEnd(Instant.now().minusSeconds(86400))
                .gracePeriodEnd(Instant.now().plusSeconds(86400 * 4))
                .build();
        assertThat(subGrace.isOperable()).isTrue();

        // Suspended
        Subscription subSuspended = Subscription.builder()
                .organizationId(UUID.randomUUID())
                .plan(plan)
                .status(SubscriptionStatus.SUSPENDED)
                .currentPeriodStart(Instant.now().minusSeconds(86400 * 35))
                .currentPeriodEnd(Instant.now().minusSeconds(86400 * 5))
                .gracePeriodEnd(Instant.now().minusSeconds(86400))
                .build();
        assertThat(subSuspended.isOperable()).isFalse();
    }

    @Test
    @DisplayName("Should validate PromoCode checkout criteria")
    void testPromoCodeValidation() {
        Campaign campaign = Campaign.builder()
                .name("Summer Influencer Push")
                .discountValue(new BigDecimal("20.00"))
                .commissionValue(new BigDecimal("30.00"))
                .startDate(Instant.now().minusSeconds(86400))
                .build();

        Influencer influencer = Influencer.builder()
                .name("Rahul Sharma")
                .email("rahul@creators.com")
                .instagramHandle("@rahulautomates")
                .build();

        PromoCode promo = PromoCode.builder()
                .campaign(campaign)
                .influencer(influencer)
                .code("RAHUL20")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .commissionType(CommissionType.PERCENTAGE)
                .commissionValue(new BigDecimal("30.00"))
                .maxUses(100)
                .currentUses(5)
                .minimumPurchaseInr(new BigDecimal("399.00"))
                .startDate(Instant.now().minusSeconds(86400))
                .expiryDate(Instant.now().plusSeconds(86400 * 30))
                .status("ACTIVE")
                .build();

        // Eligible checkout
        assertThat(promo.isValidForCheckout(new BigDecimal("499.00"))).isTrue();

        // Below minimum purchase
        assertThat(promo.isValidForCheckout(new BigDecimal("299.00"))).isFalse();

        // Expired status
        promo.setStatus("DISABLED");
        assertThat(promo.isValidForCheckout(new BigDecimal("499.00"))).isFalse();
    }
}
