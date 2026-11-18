package com.bnaqica.crmdemousermanagementservice.api;

import com.bnaqica.crmdemousermanagementservice.dto.AuditSearchCriteria;
import com.bnaqica.crmdemousermanagementservice.dto.response.AuditPageResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.AuditRecordResponse;
import com.bnaqica.crmdemousermanagementservice.service.AuditSearchService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only search of the central audit store. Nothing here creates, changes or deletes a record (405). */
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AuditAdminController {

    private final AuditSearchService audit;

    @GetMapping
    AuditPageResponse search(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String realm,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String ticketRef,
            @RequestParam(required = false) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return audit.search(
                AuditSearchCriteria.builder()
                        .from(from)
                        .to(to)
                        .source(source)
                        .realm(realm)
                        .actorId(actorId)
                        .targetId(targetId)
                        .action(action)
                        .outcome(outcome)
                        .ticketRef(ticketRef)
                        .correlationId(correlationId)
                        .build(),
                page,
                size);
    }

    @GetMapping("/{eventId}")
    AuditRecordResponse get(@PathVariable UUID eventId) {
        return audit.get(eventId);
    }
}
