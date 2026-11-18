package com.bnaqica.crmdemousermanagementservice.repository;

import com.bnaqica.crmdemousermanagementservice.entity.Permission;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    List<Permission> findByService(String service);

    Optional<Permission> findByRealmAndKey(UserType realm, String key);

    List<Permission> findByRealmAndKeyIn(UserType realm, Collection<String> keys);
}
