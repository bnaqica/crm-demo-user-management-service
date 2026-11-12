package com.bnaqica.crmdemousermanagementservice.entity;

import static com.bnaqica.crmdemousermanagementservice.entity.UserStatus.ACTIVE;
import static com.bnaqica.crmdemousermanagementservice.entity.UserStatus.DISABLED;
import static com.bnaqica.crmdemousermanagementservice.entity.UserStatus.INVITED;
import static com.bnaqica.crmdemousermanagementservice.entity.UserStatus.STARTER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** user-accounts spec, "Account status transitions". */
class UserStatusTests {

    private static final Set<String> ALLOWED =
            Set.of("INVITED->ACTIVE", "STARTER->ACTIVE", "ACTIVE->DISABLED", "DISABLED->ACTIVE");

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    void allowsOnlySpecifiedTransitions(UserStatus from) {
        for (UserStatus to : UserStatus.values()) {
            boolean expected = ALLOWED.contains(from + "->" + to);
            assertThat(from.canBecome(to)).as(from + " -> " + to).isEqualTo(expected);
        }
    }

    @Test
    void invitedUserCannotBeDisabled() {
        assertThat(INVITED.canBecome(DISABLED)).isFalse();
        assertThat(STARTER.canBecome(ACTIVE)).isTrue();
    }
}
