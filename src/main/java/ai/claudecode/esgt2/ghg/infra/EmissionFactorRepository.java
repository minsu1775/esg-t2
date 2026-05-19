package ai.claudecode.esgt2.ghg.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface EmissionFactorRepository extends JpaRepository<EmissionFactorJpaEntity, UUID> {

    Optional<EmissionFactorJpaEntity> findBySourceAndCategoryAndSubCategoryAndCountryCodeAndReportingYear(
        String source, String category, String subCategory, String countryCode, int reportingYear);

    // resolveAt: subCategory 포함 산출 시점 기준 유효한 계수 조회 (과거 재현성, L-0-09)
    @Query("""
        SELECT e FROM EmissionFactorJpaEntity e
        WHERE e.category = :category
          AND (:subCategory IS NULL OR e.subCategory = :subCategory)
          AND (:countryCode IS NULL OR e.countryCode = :countryCode)
          AND e.effectiveFrom <= :date
          AND (e.effectiveTo IS NULL OR e.effectiveTo >= :date)
          AND e.isActive = true
        ORDER BY e.effectiveFrom DESC
        """)
    Optional<EmissionFactorJpaEntity> findActiveAt(
        @Param("category") String category,
        @Param("subCategory") String subCategory,
        @Param("countryCode") String countryCode,
        @Param("date") LocalDate date);
}
