package com.bnaqica.crmdemousermanagementservice.api;

import com.bnaqica.crmdemousermanagementservice.dto.response.PermissionResponse;
import com.bnaqica.crmdemousermanagementservice.service.PermissionCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The permission catalog for admins. Read-only: permissions come from the services' code, so there is no
 * create, change or delete here (they answer 405).
 */
@RestController
@RequiredArgsConstructor
public class CatalogAdminController {

    private final PermissionCatalogService catalog;

    @GetMapping("/admin/catalog")
    List<PermissionResponse> list(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String realm,
            @RequestParam(required = false) Boolean deprecated) {
        return catalog.list(service, realm, deprecated);
    }
}
