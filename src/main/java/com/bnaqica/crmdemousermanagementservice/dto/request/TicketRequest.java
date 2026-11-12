package com.bnaqica.crmdemousermanagementservice.dto.request;

/** Body of an account action such as disable or unlock: only the ticket it is done under. */
public record TicketRequest(@TicketReference String ticketRef) {}
