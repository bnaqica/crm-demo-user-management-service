package com.bnaqica.crmdemousermanagementservice.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Removes secrets from audit payloads before they are stored: at any depth, every field whose name mentions a
 * password, secret, token or credential, and, inside {@code attributes}, the user attributes listed in
 * {@code UMS_AUDIT_SENSITIVE_ATTRIBUTES}.
 */
@Component
public class AuditSanitizer {

    private static final List<String> SECRET_WORDS =
            List.of("password", "secret", "token", "credential", "otp", "privatekey", "private_key");

    private final JsonMapper jsonMapper;
    private final Set<String> sensitiveAttributes;

    public AuditSanitizer(
            JsonMapper jsonMapper, @Value("${ums.audit.sensitive-attributes:}") List<String> sensitiveAttributes) {
        this.jsonMapper = jsonMapper;
        this.sensitiveAttributes = sensitiveAttributes.stream()
                .map(name -> name.strip().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
    }

    /** A cleaned copy; the input is left as it was. */
    public JsonNode clean(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        JsonNode copy = payload.deepCopy();
        strip(copy, false);
        return copy;
    }

    /** Parses JSON text (such as a Keycloak admin event's representation) and cleans it; null stays null. */
    public JsonNode cleanText(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return clean(jsonMapper.readTree(json));
        } catch (JacksonException ex) {
            // Text that isn't JSON could hold anything, so none of it is stored
            return jsonMapper.createObjectNode().put("representation", "unreadable");
        }
    }

    /** Keycloak's UPDATE_PROFILE details name changed attributes {@code previous_<name>} and {@code updated_<name>}. */
    private boolean isChangedSensitiveAttribute(String lower) {
        for (String prefix : List.of("previous_", "updated_")) {
            if (lower.startsWith(prefix) && sensitiveAttributes.contains(lower.substring(prefix.length()))) {
                return true;
            }
        }
        return false;
    }

    private void strip(JsonNode node, boolean inAttributes) {
        if (node instanceof ObjectNode object) {
            List<String> names = object.propertyNames().stream().toList();
            for (String name : names) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (SECRET_WORDS.stream().anyMatch(lower::contains)
                        || (inAttributes && sensitiveAttributes.contains(lower))
                        || isChangedSensitiveAttribute(lower)) {
                    object.remove(name);
                } else {
                    strip(object.get(name), "attributes".equals(lower));
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(element -> strip(element, false));
        }
    }
}
