package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl implements AdminAccountService {

    private final AdminAccounts admins;
    private final AuditEventPublisher audit;

    @Override
    public void disable(UUID id, String ticketRef) {
        audit.audited(AuditAction.ADMIN_DISABLED, id.toString(), ticketRef, () -> {
            admins.disable(id, ticketRef);
            return null;
        });
    }

    @Override
    public void delete(UUID id, String ticketRef) {
        audit.audited(AuditAction.ADMIN_DELETED, id.toString(), ticketRef, () -> {
            admins.delete(id, ticketRef);
            return null;
        });
    }
}
