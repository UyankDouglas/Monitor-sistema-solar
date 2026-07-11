package com.solarmonitor.user.repository;

import com.solarmonitor.user.domain.Role;
import com.solarmonitor.user.domain.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Short> {

    Optional<Role> findByName(RoleName name);
}
