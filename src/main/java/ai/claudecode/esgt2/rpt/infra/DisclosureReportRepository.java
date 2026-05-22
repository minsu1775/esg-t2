package ai.claudecode.esgt2.rpt.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisclosureReportRepository extends JpaRepository<DisclosureReportJpaEntity, UUID> {

    Optional<DisclosureReportJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<DisclosureReportJpaEntity> findByTenantIdAndEntityIdAndReportingYear(
        UUID tenantId, UUID entityId, int reportingYear);

    /** 전년 보고서 APPROVED 1건 — YoY 비교용 */
    Optional<DisclosureReportJpaEntity> findFirstByTenantIdAndEntityIdAndReportingYearAndStatus(
        UUID tenantId, UUID entityId, int reportingYear, String status);
}
