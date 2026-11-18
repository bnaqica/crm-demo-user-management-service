package com.bnaqica.crmdemousermanagementservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new admin account in crm-admin; created only once a second admin approves. */
public record CreateAdminRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName,
        @TicketReference String ticketRef) {}
