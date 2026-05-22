package ai.claudecode.esgt2.supply.infra;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_invitations")
@Getter
@NoArgsConstructor
public class SupplierInvitationJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true)
    private UUID token;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private UUID invitedBy;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    private OffsetDateTime acceptedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    public SupplierInvitationJpaEntity(UUID id, UUID tenantId, UUID entityId, String email,
                                        UUID token, UUID invitedBy, OffsetDateTime expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.entityId = entityId;
        this.email = email;
        this.token = token;
        this.status = "PENDING";
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
    }

    /** PENDING → ACCEPTED */
    public void accept() {
        if (!"PENDING".equals(this.status)) {
            throw new IllegalStateException("PENDING 상태만 수락 가능합니다.");
        }
        this.status = "ACCEPTED";
        this.acceptedAt = OffsetDateTime.now();
    }
}
