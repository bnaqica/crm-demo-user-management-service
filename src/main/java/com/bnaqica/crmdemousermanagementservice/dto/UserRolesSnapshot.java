package com.bnaqica.crmdemousermanagementservice.dto;

import java.util.List;

/** The roles a user holds, for the before and after of an audit event. */
public record UserRolesSnapshot(List<String> roles) {}
