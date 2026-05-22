package ai.claudecode.esgt2.ghg.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FormulaVersionRepository extends JpaRepository<FormulaVersionJpaEntity, UUID> {

    List<FormulaVersionJpaEntity> findByCode(String code);

    List<FormulaVersionJpaEntity> findByCodeAndStatus(String code, String status);

    /**
     * 동일 code의 모든 ACTIVE 버전을 INACTIVE로 일괄 처리.
     * 신규 버전 등록 전 호출 — 하나의 code에 ACTIVE 버전은 최대 1개.
     */
    @Modifying
    @Query("UPDATE FormulaVersionJpaEntity f SET f.status = 'INACTIVE' WHERE f.code = :code AND f.status = 'ACTIVE'")
    void deactivateAllByCode(@Param("code") String code);
}
