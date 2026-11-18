package com.bnaqica.crmdemousermanagementservice.api;

import com.bnaqica.crmdemousermanagementservice.dto.request.CatalogRegistrationRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.CatalogRegistrationResponse;
import com.bnaqica.crmdemousermanagementservice.service.PermissionCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** For services: a crm-internal client-credentials token with scope {@code ums:catalog}, for its own name only. */
@RestController
@RequiredArgsConstructor
public class PermissionCatalogController {

    private final PermissionCatalogService catalog;

    @PutMapping("/internal/catalog/{service}")
    CatalogRegistrationResponse register(
            @PathVariable String service, @Valid @RequestBody CatalogRegistrationRequest request) {
        return catalog.register(service, request);
    }
}
