package com.bnaqica.crmdemousermanagementservice.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The admin making the current request. */
final class CurrentAdmin {

    private CurrentAdmin() {}

    /** Their sub, or {@code anonymous} in the local profile, where authentication is off. */
    static String sub() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt
                ? jwt.getToken().getSubject()
                : "anonymous";
    }
}
