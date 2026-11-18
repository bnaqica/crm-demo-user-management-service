package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.AuditRecord;
import com.bnaqica.crmdemousermanagementservice.dto.AuditSearchCriteria;
import com.bnaqica.crmdemousermanagementservice.dto.response.AuditPageResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.AuditRecordResponse;
import com.bnaqica.crmdemousermanagementservice.exception.AuditRecordNotFoundException;
import com.bnaqica.crmdemousermanagementservice.exception.AuditSearchException;
import com.bnaqica.crmdemousermanagementservice.repository.AuditRecordRepository;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditSearchServiceImpl implements AuditSearchService {

    static final int MAX_PAGE_SIZE = 200;

    private final AuditRecordRepository records;
    private final JsonMapper jsonMapper;
    private final Duration maxRange;

    public AuditSearchServiceImpl(
            AuditRecordRepository records,
            JsonMapper jsonMapper,
            @Value("${ums.audit.search.max-range:90d}") Duration maxRange) {
        this.records = records;
        this.jsonMapper = jsonMapper;
        this.maxRange = maxRange;
    }

    @Override
    public AuditPageResponse search(AuditSearchCriteria criteria, int page, int size) {
        if (criteria.from() == null || criteria.to() == null) {
            throw new AuditSearchException("A search needs a time range: from and to");
        }
        if (!criteria.from().isBefore(criteria.to())) {
            throw new AuditSearchException("from must be before to");
        }
        if (Duration.between(criteria.from(), criteria.to()).compareTo(maxRange) > 0) {
            throw new AuditSearchException("The time range can be at most " + maxRange.toDays() + " days");
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AuditSearchException("page must be 0 or more and size 1 to " + MAX_PAGE_SIZE);
        }
        long total = records.count(criteria);
        return new AuditPageResponse(
                records.search(criteria, page, size).stream()
                        .map(this::toResponse)
                        .toList(),
                page,
                size,
                total,
                (int) ((total + size - 1) / size));
    }

    @Override
    public AuditRecordResponse get(UUID eventId) {
        return toResponse(records.find(eventId).orElseThrow(() -> new AuditRecordNotFoundException(eventId)));
    }

    private AuditRecordResponse toResponse(AuditRecord record) {
        return AuditRecordResponse.builder()
                .eventId(record.eventId())
                .occurredAt(record.occurredAt())
                .source(record.source())
                .realm(record.realm())
                .actorId(record.actorId())
                .actorType(record.actorType())
                .action(record.action())
                .targetType(record.targetType())
                .targetId(record.targetId())
                .outcome(record.outcome())
                .ticketRef(record.ticketRef())
                .before(record.before() == null ? null : jsonMapper.readTree(record.before()))
                .after(record.after() == null ? null : jsonMapper.readTree(record.after()))
                .ipAddress(record.ipAddress())
                .correlationId(record.correlationId())
                .reason(record.reason())
                .build();
    }
}
