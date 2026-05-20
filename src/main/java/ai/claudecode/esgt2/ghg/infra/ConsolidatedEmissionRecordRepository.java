package ai.claudecode.esgt2.ghg.infra;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

// Append-only: 연결 집계 결과는 INSERT-only (P1 재현성 원칙)
public interface ConsolidatedEmissionRecordRepository
        extends Repository<ConsolidatedEmissionRecordJpaEntity, UUID> {

    ConsolidatedEmissionRecordJpaEntity save(ConsolidatedEmissionRecordJpaEntity entity);

    List<ConsolidatedEmissionRecordJpaEntity> findByTenantIdAndRootEntityIdAndReportingYear(
        UUID tenantId, UUID rootEntityId, int reportingYear);
}
