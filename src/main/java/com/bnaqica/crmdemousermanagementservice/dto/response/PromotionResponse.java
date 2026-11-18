package com.bnaqica.crmdemousermanagementservice.dto.response;

import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import java.util.List;
import java.util.UUID;

/** @param roles the user's portal roles after the call */
public record PromotionResponse(UUID sub, UserStatus status, List<String> roles) {}
