package ai.claudecode.esgt2.ghg.infra;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

// Append-only: 기여분 상세도 INSERT-only
public interface ConsolidatedEmissionContributionRepository
        extends Repository<ConsolidatedEmissionContributionJpaEntity, UUID> {

    ConsolidatedEmissionContributionJpaEntity save(ConsolidatedEmissionContributionJpaEntity entity);

    List<ConsolidatedEmissionContributionJpaEntity> findByConsolidatedRecordId(UUID consolidatedRecordId);
}
