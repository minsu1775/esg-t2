package ai.claudecode.esgt2.ghg.infra.evidence;

import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface EvidenceFileRepository extends Repository<EvidenceFileJpaEntity, UUID> {
    EvidenceFileJpaEntity save(EvidenceFileJpaEntity entity);
}
