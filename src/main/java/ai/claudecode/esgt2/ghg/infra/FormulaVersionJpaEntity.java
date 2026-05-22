package ai.claudecode.esgt2.ghg.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "formula_versions")
@Getter
@NoArgsConstructor
public class FormulaVersionJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String expression;

    @Column(length = 50)
    private String ghgCategory;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String yamlContent;

    @Column(nullable = false, length = 20)
    private String status;   // ACTIVE / INACTIVE

    private UUID activatedBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Builder
    public FormulaVersionJpaEntity(UUID id, String code, String version, String expression,
                                    String ghgCategory, String yamlContent, UUID activatedBy) {
        this.id = id != null ? id : UUID.randomUUID();
        this.code = code;
        this.version = version;
        this.expression = expression;
        this.ghgCategory = ghgCategory;
        this.yamlContent = yamlContent;
        this.status = "ACTIVE";
        this.activatedBy = activatedBy;
        this.createdAt = Instant.now();
    }

    /** 비활성화 — DELETE 없음, status만 INACTIVE로 변경 (P1) */
    public void deactivate() {
        this.status = "INACTIVE";
    }
}
