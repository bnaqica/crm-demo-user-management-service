package com.bnaqica.crmdemousermanagementservice.dto.response;

import java.util.List;

/**
 * @param active the service's permissions after the registration
 * @param deprecated those it no longer registers; still listed, not grantable
 */
public record CatalogRegistrationResponse(String service, List<String> active, List<String> deprecated) {}
