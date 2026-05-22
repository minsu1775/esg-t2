package ai.claudecode.esgt2.vw.infra;

import ai.claudecode.esgt2.vw.domain.VerificationSignature;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_signatures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationSignatureJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "snapshot_id", nullable = false, unique = true)
    private UUID snapshotId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "signed_by", nullable = false)
    private UUID signedBy;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @Column(name = "sign_note")
    private String signNote;

    public static VerificationSignatureJpaEntity fromDomain(VerificationSignature domain) {
        var entity = new VerificationSignatureJpaEntity();
        entity.id = domain.id();
        entity.snapshotId = domain.snapshotId();
        entity.tenantId = domain.tenantId();
        entity.signedBy = domain.signedBy();
        entity.signedAt = domain.signedAt();
        entity.signNote = domain.signNote();
        return entity;
    }

    public VerificationSignature toDomain() {
        return new VerificationSignature(id, snapshotId, tenantId, signedBy, signedAt, signNote);
    }
}
