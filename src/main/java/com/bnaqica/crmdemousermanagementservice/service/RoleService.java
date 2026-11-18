package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.entity.Role;
import java.util.List;
import java.util.Optional;

/** CRUD for {@code role} and its {@code role_permission} rows. The only path to its repository. */
public interface RoleService {

    String SUPER_MANAGER = "super-manager";

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.RoleConflictException if the name is taken */
    Role create(Role role);

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.RoleNotFoundException if there is none */
    Role get(String name);

    Optional<Role> find(String name);

    /** {@link #get}, locking the row until the caller's transaction ends. Needs a transaction. */
    Role lockForUpdate(String name);

    Role update(Role role);

    /** Every role, by name. */
    List<Role> findAll();
}
