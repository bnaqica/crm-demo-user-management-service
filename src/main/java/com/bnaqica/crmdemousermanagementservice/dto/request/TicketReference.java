package com.bnaqica.crmdemousermanagementservice.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A required support-ticket reference such as {@code OPS-12}. Every admin write carries one. */
@NotBlank
@Pattern(regexp = "^[A-Z][A-Z0-9]*-\\d+$")
@Constraint(validatedBy = {})
@Documented
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface TicketReference {

    String message() default "must be a ticket reference such as OPS-12";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
