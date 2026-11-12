package com.bnaqica.crmdemousermanagementservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param role the portal role the user starts with, such as {@code external-standard} */
public record InviteExternalUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName,
        @NotBlank String role,
        @TicketReference String ticketRef) {}
