package ai.claudecode.esgt2.ghg.infra;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

// Append-only: 배출량 기록은 INSERT-only (P1 재현성 원칙, 08-persistence.md)
public interface EmissionRecordRepository extends Repository<EmissionRecordJpaEntity, UUID> {

    EmissionRecordJpaEntity save(EmissionRecordJpaEntity entity);

    List<EmissionRecordJpaEntity> findByTenantIdAndEntityIdAndReportingYear(
        UUID tenantId, UUID entityId, int reportingYear);
}
