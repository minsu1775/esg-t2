package ai.claudecode.esgt2.ghg.infra;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Append-only: 배출량 기록은 INSERT-only (P1 재현성 원칙, 08-persistence.md)
public interface EmissionRecordRepository extends Repository<EmissionRecordJpaEntity, UUID> {

    EmissionRecordJpaEntity save(EmissionRecordJpaEntity entity);

    Optional<EmissionRecordJpaEntity> findById(UUID id);

    List<EmissionRecordJpaEntity> findByTenantIdAndEntityIdAndReportingYear(
        UUID tenantId, UUID entityId, int reportingYear);

    // 연결 집계용: 여러 법인 배출량을 단일 IN 쿼리로 조회 (N+1 방지)
    List<EmissionRecordJpaEntity> findByTenantIdAndEntityIdInAndReportingYear(
        UUID tenantId, Collection<UUID> entityIds, int reportingYear);

    // 커버리지 리포트용: 해당 법인/연도의 Scope3 배출 기록 전체 조회
    @Query("SELECT e FROM EmissionRecordJpaEntity e " +
           "WHERE e.tenantId = :tenantId AND e.entityId = :entityId " +
           "AND e.reportingYear = :reportingYear AND e.scope = 'SCOPE3'")
    List<EmissionRecordJpaEntity> findScope3ByTenantIdAndEntityIdAndReportingYear(
        @Param("tenantId") UUID tenantId,
        @Param("entityId") UUID entityId,
        @Param("reportingYear") int reportingYear);
}
