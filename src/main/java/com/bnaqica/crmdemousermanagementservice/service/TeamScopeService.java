package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.response.TeamScopeResponse;
import java.util.UUID;

/** The teams an internal user can see CRM data for, for CRM services. */
public interface TeamScopeService {

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.UserNotFoundException unless the sub is an INTERNAL user */
    TeamScopeResponse scope(UUID sub);
}
