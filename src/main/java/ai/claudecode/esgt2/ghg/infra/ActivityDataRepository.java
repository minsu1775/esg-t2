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
}
