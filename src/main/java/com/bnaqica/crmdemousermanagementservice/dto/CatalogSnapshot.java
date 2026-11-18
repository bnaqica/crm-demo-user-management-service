package com.bnaqica.crmdemousermanagementservice.dto;

import java.util.List;

/** A service's catalog entries, for the before and after of an audit event. */
public record CatalogSnapshot(List<String> active, List<String> deprecated) {}
