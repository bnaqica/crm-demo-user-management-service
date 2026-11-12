package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.config.UmsProperties;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.EmailAlreadyInUseException;
import com.bnaqica.crmdemousermanagementservice.exception.InvalidUserRequestException;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The email rules shared by creating and updating users: one address per account across both realms, and domains that match the user type. */
@Component
@RequiredArgsConstructor
class EmailRules {

    private final KeycloakClients keycloak;
    private final UserAccountService accounts;
    private final UmsProperties properties;

    static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Checks the UMS and both realms, so accounts created in Keycloak outside the UMS count too. */
    void checkFree(String email) {
        checkFree(email, null);
    }

    /** As {@link #checkFree(String)}, but the records of {@code owner} (the user being changed) don't count. */
    void checkFree(String email, UUID owner) {
        String ownerId = owner == null ? null : owner.toString();
        boolean taken = accounts.findByEmail(email)
                        .filter(account -> !account.getSub().equals(owner))
                        .isPresent()
                || Arrays.stream(UserType.values())
                        .anyMatch(type -> keycloak.forType(type).findByEmail(email, true).stream()
                                .anyMatch(user -> !user.id().equals(ownerId)));
        if (taken) {
            throw new EmailAlreadyInUseException();
        }
    }

    void checkDomain(UserType type, String email) {
        String domain = email.substring(email.indexOf('@') + 1);
        boolean corporate = properties.corporateDomains().contains(domain);
        if (type == UserType.INTERNAL && !corporate) {
            throw new InvalidUserRequestException("Internal users must have a corporate email address");
        }
        if (type == UserType.EXTERNAL && corporate) {
            throw new InvalidUserRequestException("External users can't have a corporate email address");
        }
    }
}
