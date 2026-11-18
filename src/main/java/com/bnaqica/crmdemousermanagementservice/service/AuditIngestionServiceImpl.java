package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.AuditRecord;
import com.bnaqica.crmdemousermanagementservice.exception.InvalidAuditEventException;
import com.bnaqica.crmdemousermanagementservice.repository.AuditRecordRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps each event to the common audit record, strips secrets and inserts it. An event id already stored is
 * skipped, so redelivery is harmless.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditIngestionServiceImpl implements AuditIngestionService {

    static final String KEYCLOAK = "KEYCLOAK";

    private final JsonMapper jsonMapper;
    private final AuditSanitizer sanitizer;
    private final AuditRecordRepository records;

    @Override
    public void ingestAuditEvent(String payload) {
        JsonNode event = parse(payload);
        AuditRecord record = AuditRecord.builder()
                .eventId(uuid(required(event, "eventId")))
                .occurredAt(instant(required(event, "occurredAt")))
                .source(required(event, "source"))
                .realm(text(event, "realm"))
                .actorId(text(event, "actorId"))
                .actorType(text(event, "actorType"))
                .action(required(event, "action"))
                .targetType(text(event, "targetType"))
                .targetId(text(event, "targetId"))
                .outcome(text(event, "outcome"))
                .ticketRef(text(event, "ticketRef"))
                .before(json(sanitizer.clean(object(event, "before"))))
                .after(json(sanitizer.clean(object(event, "after"))))
                .ipAddress(text(event, "ipAddress"))
                .correlationId(text(event, "correlationId"))
                .reason(text(event, "reason"))
                .build();
        store(record);
    }

    @Override
    public void ingestKeycloakEvent(String payload) {
        JsonNode event = parse(payload);
        String id = required(event, "id");
        JsonNode time = event.get("time");
        if (time == null || !time.isNumber()) {
            throw new InvalidAuditEventException("Keycloak event " + id + " has no time", null);
        }
        String error = text(event, "error");
        AuditRecord.AuditRecordBuilder record = AuditRecord.builder()
                .eventId(keycloakEventId(id))
                .occurredAt(Instant.ofEpochMilli(time.asLong()))
                .source(KEYCLOAK)
                .realm(required(event, "realm"))
                .reason(error);
        if ("ADMIN".equals(text(event, "kind"))) {
            JsonNode auth = object(event, "authDetails");
            String resourceType = required(event, "resourceType");
            String path = text(event, "resourcePath");
            record.action("ADMIN_" + resourceType + "_" + required(event, "operationType"))
                    .actorId(auth == null ? null : text(auth, "userId"))
                    .actorType("ADMIN")
                    .ipAddress(auth == null ? null : text(auth, "ipAddress"))
                    .targetType(targetType(resourceType, path))
                    .targetId(targetId(path))
                    .after(json(sanitizer.cleanText(text(event, "representation"))));
        } else {
            String type = required(event, "type");
            JsonNode details = object(event, "details");
            record.action(type)
                    .actorId(text(event, "userId"))
                    .actorType("USER")
                    .targetType("USER")
                    .targetId(text(event, "userId"))
                    .ipAddress(details == null ? null : text(details, "ipAddress"))
                    .after(json(sanitizer.clean(details)));
            error = error == null && type.endsWith("_ERROR") ? type : error;
        }
        record.outcome(error == null ? "SUCCESS" : "FAILURE");
        store(record.build());
    }

    /** {@code users/{id}/role-mappings/realm} is about user {id}; the sub-resource is in the action. */
    private static final Map<String, String> PATH_TARGETS = Map.of(
            "users",
            "USER",
            "groups",
            "GROUP",
            "clients",
            "CLIENT",
            "roles-by-id",
            "ROLE",
            "client-scopes",
            "CLIENT_SCOPE");

    static String targetType(String resourceType, String path) {
        String[] segments = path == null ? new String[0] : path.split("/");
        return segments.length >= 2 && PATH_TARGETS.containsKey(segments[0])
                ? PATH_TARGETS.get(segments[0])
                : resourceType;
    }

    static String targetId(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] segments = path.split("/");
        return segments.length >= 2 && PATH_TARGETS.containsKey(segments[0])
                ? segments[1]
                : segments[segments.length - 1];
    }

    private void store(AuditRecord record) {
        if (records.insert(record)) {
            log.debug("Stored audit record {} ({} {})", record.eventId(), record.source(), record.action());
        } else {
            log.debug("Audit record {} is stored already; skipped", record.eventId());
        }
    }

    /** Keycloak ids are UUIDs; anything else gets a stable UUID derived from it, so redelivery still dedups. */
    static UUID keycloakEventId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(("keycloak-event:" + id).getBytes(StandardCharsets.UTF_8));
        }
    }

    private JsonNode parse(String payload) {
        try {
            JsonNode event = jsonMapper.readTree(payload);
            if (event == null || !event.isObject()) {
                throw new InvalidAuditEventException("Audit event isn't a JSON object", null);
            }
            return event;
        } catch (JacksonException ex) {
            throw new InvalidAuditEventException("Audit event isn't valid JSON", ex);
        }
    }

    private static String required(JsonNode event, String field) {
        String value = text(event, field);
        if (value == null || value.isBlank()) {
            throw new InvalidAuditEventException("Audit event without " + field, null);
        }
        return value;
    }

    private static String text(JsonNode event, String field) {
        JsonNode value = event.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static JsonNode object(JsonNode event, String field) {
        JsonNode value = event.get(field);
        return value == null || value.isNull() ? null : value;
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidAuditEventException("eventId isn't a UUID", ex);
        }
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ex) {
            throw new InvalidAuditEventException("occurredAt isn't an ISO-8601 instant", ex);
        }
    }

    private static String json(JsonNode node) {
        return node == null ? null : node.toString();
    }
}
