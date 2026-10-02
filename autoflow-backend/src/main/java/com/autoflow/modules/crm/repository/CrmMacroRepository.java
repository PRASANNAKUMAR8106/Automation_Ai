package com.autoflow.modules.crm.repository;

import com.autoflow.modules.crm.entity.CrmMacro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrmMacroRepository extends JpaRepository<CrmMacro, UUID> {

    List<CrmMacro> findByOrganizationIdOrderByNameAsc(UUID organizationId);

    Optional<CrmMacro> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
