package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.entity.Permission;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** CRUD for {@code permission}. The only path to its repository. Permissions are never deleted. */
public interface PermissionService {

    Permission save(Permission permission);

    List<Permission> findByService(String service);

    List<Permission> findAll();

    Optional<Permission> find(UserType realm, String key);

    /** Those of {@code keys} that exist in the realm. */
    List<Permission> findAll(UserType realm, Collection<String> keys);
}
