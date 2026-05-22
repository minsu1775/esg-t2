package ai.claudecode.esgt2.vw.infra;

import ai.claudecode.esgt2.vw.domain.VerificationSnapshot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationSnapshotJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "snapshot_hash", nullable = false, length = 64)
    private String snapshotHash;

    @Column(name = "snapshot_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String snapshotData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "frozen_at", nullable = false)
    private Instant frozenAt;

    public static VerificationSnapshotJpaEntity fromDomain(VerificationSnapshot domain) {
        var entity = new VerificationSnapshotJpaEntity();
        entity.id = domain.id();
        entity.tenantId = domain.tenantId();
        entity.reportId = domain.reportId();
        entity.snapshotHash = domain.snapshotHash();
        entity.snapshotData = domain.snapshotDataJson();
        entity.createdAt = domain.createdAt();
        entity.frozenAt = domain.frozenAt();
        return entity;
    }

    public VerificationSnapshot toDomain() {
        return new VerificationSnapshot(
            id, tenantId, reportId, snapshotHash, snapshotData, createdAt, frozenAt
        );
    }
}
