package com.bnaqica.crmdemousermanagementservice.api;

import com.bnaqica.crmdemousermanagementservice.dto.request.CreateAdminRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.TicketRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.GrantRequestResponse;
import com.bnaqica.crmdemousermanagementservice.service.AdminAccountService;
import com.bnaqica.crmdemousermanagementservice.service.PrivilegedGrantService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin accounts in crm-admin. Creating one needs a second admin, so it answers 202 with the grant request. */
@RestController
@RequestMapping("/admin/admins")
@RequiredArgsConstructor
public class AdminAccountAdminController {

    private final PrivilegedGrantService grants;
    private final AdminAccountService admins;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    GrantRequestResponse create(@Valid @RequestBody CreateAdminRequest request) {
        return grants.requestAdminAccount(request);
    }

    /** 409 for the last enabled admin. */
    @PostMapping("/{id}/disable")
    void disable(@PathVariable UUID id, @Valid @RequestBody TicketRequest request) {
        admins.disable(id, request.ticketRef());
    }

    /** 409 for the last enabled admin. Carries the ticket in the body, like every other admin write. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @Valid @RequestBody TicketRequest request) {
        admins.delete(id, request.ticketRef());
    }
}
