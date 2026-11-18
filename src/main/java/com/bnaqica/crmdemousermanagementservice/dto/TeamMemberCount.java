package com.bnaqica.crmdemousermanagementservice.dto;

import java.util.UUID;

/** How many members and managers a team has; a query projection. */
public record TeamMemberCount(UUID teamId, long members, long managers) {}
