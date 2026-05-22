package ai.claudecode.esgt2.vw.infra;

import ai.claudecode.esgt2.vw.domain.VerificationComment;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationCommentJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static VerificationCommentJpaEntity fromDomain(VerificationComment domain) {
        var entity = new VerificationCommentJpaEntity();
        entity.id = domain.id();
        entity.snapshotId = domain.snapshotId();
        entity.tenantId = domain.tenantId();
        entity.authorId = domain.authorId();
        entity.body = domain.body();
        entity.createdAt = domain.createdAt();
        return entity;
    }

    public VerificationComment toDomain() {
        return new VerificationComment(id, snapshotId, tenantId, authorId, body, createdAt);
    }
}
