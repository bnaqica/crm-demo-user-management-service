package com.bnaqica.crmdemousermanagementservice.api;

import com.bnaqica.crmdemousermanagementservice.dto.request.TicketRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.GrantRequestResponse;
import com.bnaqica.crmdemousermanagementservice.entity.GrantRequestStatus;
import com.bnaqica.crmdemousermanagementservice.service.PrivilegedGrantService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin API for privileged grant requests. They are opened by the actions that need them, never directly. */
@RestController
@RequestMapping("/admin/grant-requests")
@RequiredArgsConstructor
public class GrantRequestAdminController {

    private final PrivilegedGrantService grants;

    @GetMapping
    List<GrantRequestResponse> list(@RequestParam(required = false) GrantRequestStatus status) {
        return grants.list(status);
    }

    @GetMapping("/{id}")
    GrantRequestResponse get(@PathVariable UUID id) {
        return grants.get(id);
    }

    @PostMapping("/{id}/approve")
    GrantRequestResponse approve(@PathVariable UUID id, @Valid @RequestBody TicketRequest request) {
        return grants.approve(id, request.ticketRef());
    }

    @PostMapping("/{id}/reject")
    GrantRequestResponse reject(@PathVariable UUID id, @Valid @RequestBody TicketRequest request) {
        return grants.reject(id, request.ticketRef());
    }
}
