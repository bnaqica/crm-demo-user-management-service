package com.bnaqica.crmdemousermanagementservice.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Internal users live in the {@code crm-internal} realm, external users in {@code portal}. */
@Getter
@RequiredArgsConstructor
public enum UserType {
    INTERNAL("crm-internal"),
    EXTERNAL("portal");

    private final String realm;

    /** The user type whose users live in {@code realm}. */
    public static java.util.Optional<UserType> ofRealm(String realm) {
        return java.util.Arrays.stream(values())
                .filter(type -> type.realm.equals(realm))
                .findFirst();
    }
}
