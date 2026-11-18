package com.bnaqica.crmdemousermanagementservice.dto.response;

import java.util.UUID;

public record MultiTeamResponse(UUID sub, boolean multiTeamAllowed) {}
