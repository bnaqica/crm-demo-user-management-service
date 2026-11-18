package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.request.CatalogRegistrationRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.CatalogRegistrationResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.PermissionResponse;
import java.util.List;

/** The permission catalog: written only by the services that enforce the permissions, read by admins. */
public interface PermissionCatalogService {

    /** Makes the service's catalog match {@code request}; what it leaves out is deprecated, not deleted. */
    CatalogRegistrationResponse register(String service, CatalogRegistrationRequest request);

    /**
     * @param realm {@code crm-internal} or {@code portal}; null for both
     * @param deprecated null for both
     */
    List<PermissionResponse> list(String service, String realm, Boolean deprecated);
}
