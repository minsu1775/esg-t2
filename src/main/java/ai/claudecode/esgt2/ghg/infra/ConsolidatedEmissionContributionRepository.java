package ai.claudecode.esgt2.ghg.infra;

import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// Append-only: 기여분 상세도 INSERT-only
public interface ConsolidatedEmissionContributionRepository
        extends Repository<ConsolidatedEmissionContributionJpaEntity, UUID> {

    <S extends ConsolidatedEmissionContributionJpaEntity> List<S> saveAll(Iterable<S> entities);

    List<ConsolidatedEmissionContributionJpaEntity> findByConsolidatedRecordId(UUID consolidatedRecordId);

    // 여러 집계 레코드의 기여분을 단일 IN 쿼리로 조회 (N+1 방지)
    List<ConsolidatedEmissionContributionJpaEntity> findByConsolidatedRecordIdIn(
        Collection<UUID> consolidatedRecordIds);
}
