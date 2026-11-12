package com.bnaqica.crmdemousermanagementservice.dto;

import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;

/**
 * Filters for the user search; null means no filter.
 *
 * @param email part of the email, ignoring case
 * @param name part of the first or last name, ignoring case
 */
public record UserSearchCriteria(String email, String name, UserType userType, UserStatus status) {}
