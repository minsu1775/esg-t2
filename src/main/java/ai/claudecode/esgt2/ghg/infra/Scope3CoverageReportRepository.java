package ai.claudecode.esgt2.ghg.infra;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

// Append-only: 커버리지 보고서는 INSERT-only (P1 재현성 원칙, 08-persistence.md)
public interface Scope3CoverageReportRepository
        extends Repository<Scope3CoverageReportJpaEntity, UUID> {

    Scope3CoverageReportJpaEntity save(Scope3CoverageReportJpaEntity entity);

    Optional<Scope3CoverageReportJpaEntity>
        findTopByTenantIdAndEntityIdAndReportingYearOrderByGeneratedAtDesc(
            UUID tenantId, UUID entityId, int reportingYear);
}
