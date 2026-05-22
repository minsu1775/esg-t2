package ai.claudecode.esgt2.ghg.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityDataRepository extends JpaRepository<ActivityDataJpaEntity, UUID> {

    List<ActivityDataJpaEntity> findByTenantIdAndEntityIdAndReportingYear(
        UUID tenantId, UUID entityId, int reportingYear);

    List<ActivityDataJpaEntity> findByTenantIdAndEntityIdAndReportingYearAndCategoryIn(
        UUID tenantId, UUID entityId, int reportingYear, List<String> categories);

    boolean existsByTenantIdAndEntityIdAndReportingYearAndCategoryAndSubCategoryAndDataSource(
        UUID tenantId, UUID entityId, int reportingYear,
        String category, String subCategory, String dataSource);

    boolean existsByTenantIdAndEntityIdAndReportingYear(UUID tenantId, UUID entityId, int reportingYear);

    java.util.Optional<ActivityDataJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    // 버전 이력 조회용 — 특정 원본의 직접 정정 목록
    List<ActivityDataJpaEntity> findByCorrectionOfAndTenantId(UUID correctionOf, UUID tenantId);

    // Formula 영향 조회용 — 특정 카테고리의 활동 데이터 전체 (ARCHIVED 포함)
    List<ActivityDataJpaEntity> findByTenantIdAndCategory(UUID tenantId, String category);
}
