package com.bnaqica.crmdemousermanagementservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** The spec allows invite links of 24 to 72 hours; anything else must stop the service from starting. */
class UmsPropertiesTests {

    @Configuration
    @EnableConfigurationProperties(UmsProperties.class)
    static class Properties {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Properties.class)
            .withPropertyValues(
                    "ums.keycloak.base-url=http://kc",
                    "ums.topics.keycloak-events=k",
                    "ums.topics.user-events=u",
                    "ums.topics.audit-events=a");

    @Test
    void defaultInviteLifespanIsAccepted() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void inviteLifespanOverSeventyTwoHoursIsRejected() {
        runner.withPropertyValues("ums.invite-lifespan=7d")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void inviteLifespanUnderTwentyFourHoursIsRejected() {
        runner.withPropertyValues("ums.invite-lifespan=1h")
                .run(context -> assertThat(context).hasFailed());
    }
}
