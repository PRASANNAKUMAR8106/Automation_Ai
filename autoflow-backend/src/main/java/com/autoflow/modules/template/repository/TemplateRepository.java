package com.autoflow.modules.template.repository;

import com.autoflow.modules.template.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TemplateRepository extends JpaRepository<Template, UUID> {
    List<Template> findAllByOrderByCreatedAtDesc();
    List<Template> findByCategoryIgnoreCaseOrderByCreatedAtDesc(String category);
    List<Template> findByIsFeaturedTrueOrderByCreatedAtDesc();
    List<Template> findByCategoryIgnoreCaseAndIsFeaturedTrueOrderByCreatedAtDesc(String category);
}
