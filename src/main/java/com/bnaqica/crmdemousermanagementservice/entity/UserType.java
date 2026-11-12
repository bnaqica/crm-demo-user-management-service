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
}
