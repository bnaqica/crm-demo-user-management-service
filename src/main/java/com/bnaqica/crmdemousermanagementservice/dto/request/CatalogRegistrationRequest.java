package com.bnaqica.crmdemousermanagementservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Every permission a service enforces; whatever it leaves out is deprecated. */
public record CatalogRegistrationRequest(@NotNull List<@Valid @NotNull Entry> permissions) {

    /**
     * @param realm {@code crm-internal} or {@code portal}
     * @param sensitive never added to Super Manager automatically; false when left out
     */
    public record Entry(
            @NotBlank @Pattern(regexp = KEY) String key,

            @NotBlank @Pattern(regexp = "crm-internal|portal")
            String realm,

            @NotBlank @Size(max = 1000) String description,
            Boolean sensitive) {

        public Entry {
            sensitive = Boolean.TRUE.equals(sensitive);
        }
    }

    /** {@code <resource>:read}, {@code <resource>:write} or {@code ui:<feature>}, lower-case. */
    public static final String KEY = "^([a-z][a-z0-9-]*:(read|write)|ui:[a-z][a-z0-9-]*)$";
}
