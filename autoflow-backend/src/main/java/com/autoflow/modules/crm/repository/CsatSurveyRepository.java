package com.autoflow.modules.crm.repository;

import com.autoflow.modules.crm.entity.CsatStatus;
import com.autoflow.modules.crm.entity.CsatSurvey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CsatSurveyRepository extends JpaRepository<CsatSurvey, UUID> {

    Optional<CsatSurvey> findByOrganizationIdAndConversationId(UUID organizationId, UUID conversationId);

    List<CsatSurvey> findAllByOrganizationId(UUID organizationId);

    List<CsatSurvey> findAllByOrganizationIdAndStatus(UUID organizationId, CsatStatus status);

    @Query("SELECT AVG(s.rating) FROM CsatSurvey s WHERE s.organizationId = :organizationId AND s.status = 'COMPLETED' AND s.rating IS NOT NULL")
    Double getAverageRatingByOrganizationId(@Param("organizationId") UUID organizationId);

    @Query("SELECT COUNT(s) FROM CsatSurvey s WHERE s.organizationId = :organizationId AND s.status = 'COMPLETED'")
    Long countCompletedSurveysByOrganizationId(@Param("organizationId") UUID organizationId);

    @Query("SELECT COUNT(s) FROM CsatSurvey s WHERE s.organizationId = :organizationId")
    Long countTotalSurveysByOrganizationId(@Param("organizationId") UUID organizationId);
}
