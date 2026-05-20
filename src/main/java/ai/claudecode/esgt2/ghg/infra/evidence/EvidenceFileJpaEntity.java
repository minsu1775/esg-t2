package ai.claudecode.esgt2.ghg.infra.evidence;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence_files")
@Getter
@NoArgsConstructor
public class EvidenceFileJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 500)
    private String originalFilename;

    @Column(nullable = false, length = 500)
    private String storedFilename;

    @Column(nullable = false, length = 2000)
    private String storageUri;

    private String mimeType;
    private Long fileSizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256Hash;

    @Column(nullable = false)
    private UUID uploadedBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Builder
    public EvidenceFileJpaEntity(UUID id, UUID tenantId, String originalFilename, String storedFilename,
                          String storageUri, String mimeType, Long fileSizeBytes,
                          String sha256Hash, UUID uploadedBy) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.storageUri = storageUri;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.sha256Hash = sha256Hash;
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }
}
