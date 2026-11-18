package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.response.PromotionResponse;
import java.util.UUID;

/** Moves a portal user from starter to standard access. Called by the CRM, never by users. */
public interface UserPromotionService {

    /**
     * Idempotent: an already promoted user is returned unchanged.
     *
     * @throws com.bnaqica.crmdemousermanagementservice.exception.UserNotFoundException for an unknown sub
     * @throws com.bnaqica.crmdemousermanagementservice.exception.PromotionConflictException for an INTERNAL or DISABLED user, or one holding neither role
     * @throws com.bnaqica.crmdemousermanagementservice.exception.RateLimitExceededException above the per-minute limit
     */
    PromotionResponse promote(UUID sub);
}
