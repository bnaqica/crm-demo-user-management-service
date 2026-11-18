package com.bnaqica.crmdemousermanagementservice.dto.response;

import java.util.List;
import java.util.UUID;

/** @param roles the UMS roles the user holds, by name */
public record UserRolesResponse(UUID sub, List<String> roles) {}
