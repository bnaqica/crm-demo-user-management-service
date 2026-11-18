package com.bnaqica.crmdemousermanagementservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** audit-store spec, "No secrets stored". */
class AuditSanitizerTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final AuditSanitizer sanitizer = new AuditSanitizer(JSON, List.of("phone"));

    private String clean(String json) {
        return sanitizer.clean(JSON.readTree(json)).toString();
    }

    @Test
    void credentialsAreRemoved() {
        assertThat(clean("""
                {"username":"pat","credentials":[{"type":"password","value":"S3cret"}]}""")).contains("pat").doesNotContain("S3cret").doesNotContain("credentials");
    }

    @Test
    void passwordsSecretsAndTokensAreRemovedAtAnyDepth() {
        assertThat(clean("""
                {"a":{"password":"p1","clientSecret":"s1","refresh_token":"t1","secretData":"d1","ok":"kept"}}"""))
                .contains("kept")
                .doesNotContain("p1")
                .doesNotContain("s1")
                .doesNotContain("t1")
                .doesNotContain("d1");
    }

    @Test
    void configuredUserAttributesAreRemoved() {
        assertThat(clean("""
                {"attributes":{"phone":["555"],"department":["sales"]}}""")).contains("sales").doesNotContain("555");
    }

    @Test
    void changedSensitiveAttributesInEventDetailsAreRemoved() {
        assertThat(clean("""
                {"previous_phone":"111","updated_phone":"222","updated_department":"ops"}""")).contains("ops").doesNotContain("111").doesNotContain("222");
    }

    @Test
    void keyMaterialIsRemoved() {
        assertThat(clean("""
                {"config":{"privateKey":["MIIEv-key"],"priority":["100"]}}""")).contains("priority").doesNotContain("MIIEv-key");
    }
}
