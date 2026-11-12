package com.bnaqica.crmdemousermanagementservice.dto.response;

import java.util.List;

/** One page of users. {@code page} counts from 0. */
public record UserPageResponse(List<UserResponse> content, int page, int size, long totalElements, int totalPages) {}
