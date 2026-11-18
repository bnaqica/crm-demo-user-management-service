package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.entity.GrantRequest;
import java.util.List;
import java.util.UUID;

/** CRUD for {@code grant_request}. The only path to its repository. */
public interface GrantRequestService {

    GrantRequest create(GrantRequest request);

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.GrantRequestNotFoundException if there is none */
    GrantRequest get(UUID id);

    /** {@link #get}, locking the row until the caller's transaction ends. Needs a transaction. */
    GrantRequest lockForUpdate(UUID id);

    GrantRequest update(GrantRequest request);

    /** Newest first. */
    List<GrantRequest> findAll();
}
