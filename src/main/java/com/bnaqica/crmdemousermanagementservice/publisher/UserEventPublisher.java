package com.bnaqica.crmdemousermanagementservice.publisher;

import com.bnaqica.crmdemousermanagementservice.config.UmsProperties;
import com.bnaqica.crmdemousermanagementservice.dto.event.UserCreated;
import com.bnaqica.crmdemousermanagementservice.dto.event.UserDisabled;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.service.OutboxService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes user events to the outbox. Call inside the transaction that makes the change, so the event is published
 * only if the change commits.
 */
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    static final int SCHEMA_VERSION = 1;

    private final OutboxService outbox;
    private final JsonMapper jsonMapper;
    private final UmsProperties properties;
    private final Clock clock;

    public void userCreated(UserAccount account) {
        publish(
                account,
                UserCreated.builder()
                        .type("UserCreated")
                        .eventId(UUID.randomUUID())
                        .schemaVersion(SCHEMA_VERSION)
                        .occurredAt(Instant.now(clock))
                        .sub(account.getSub())
                        .userType(account.getUserType())
                        .email(account.getEmail())
                        .status(account.getStatus())
                        .build());
    }

    public void userDisabled(UserAccount account) {
        publish(
                account,
                UserDisabled.builder()
                        .type("UserDisabled")
                        .eventId(UUID.randomUUID())
                        .schemaVersion(SCHEMA_VERSION)
                        .occurredAt(Instant.now(clock))
                        .sub(account.getSub())
                        .userType(account.getUserType())
                        .email(account.getEmail())
                        .build());
    }

    private void publish(UserAccount account, Object event) {
        outbox.add(properties.topics().userEvents(), account.getSub().toString(), jsonMapper.writeValueAsString(event));
    }
}
