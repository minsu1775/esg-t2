package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.api.EvidenceFileResponse;
import ai.claudecode.esgt2.ghg.api.EvidenceFileService;
import ai.claudecode.esgt2.ghg.domain.ObjectStorageGateway;
import ai.claudecode.esgt2.ghg.infra.evidence.EvidenceFileJpaEntity;
import ai.claudecode.esgt2.ghg.infra.evidence.EvidenceFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class DefaultEvidenceFileService implements EvidenceFileService {

    // 허용 확장자 목록 (allowlist, 03-security.md)
    private static final Set<String> ALLOWED_EXTENSIONS =
        Set.of("pdf", "xlsx", "xls", "csv", "png", "jpg", "jpeg");

    private final EvidenceFileRepository evidenceFileRepository;
    private final ObjectStorageGateway storageGateway;

    @Override
    @Transactional
    public EvidenceFileResponse upload(UUID tenantId, UUID uploadedBy, InputStream data,
                                       String originalFilename, String mimeType) {
        validateFilename(originalFilename);

        String extension = getExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + "." + extension;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // DigestInputStream: 단일 I/O로 업로드 + SHA-256 동시 계산 (T-3B-18, L-0-06)
            CountingDigestInputStream countingDis = new CountingDigestInputStream(data, digest);
            String storageUri = storageGateway.upload(countingDis, storedFilename, mimeType);
            String sha256Hash = HexFormat.of().formatHex(digest.digest());

            var entity = EvidenceFileJpaEntity.builder()
                .tenantId(tenantId)
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .storageUri(storageUri)
                .mimeType(mimeType)
                .fileSizeBytes(countingDis.getBytesRead())
                .sha256Hash(sha256Hash)
                .uploadedBy(uploadedBy)
                .build();

            var saved = evidenceFileRepository.save(entity);
            return new EvidenceFileResponse(saved.getId(), saved.getOriginalFilename(),
                saved.getFileSizeBytes(), saved.getSha256Hash(), saved.getCreatedAt());

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("증빙 파일 처리 실패: " + e.getMessage(), e);
        }
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("파일명이 비어 있습니다.");
        }
        // 경로 순회 방어: 슬래시·역슬래시·점-점 포함 금지 (T-3B-15, 03-security.md)
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new IllegalArgumentException("허용되지 않는 파일명: " + filename);
        }
        String extension = getExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("허용되지 않는 확장자: " + extension);
        }
    }

    private String getExtension(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        return dotIdx >= 0 ? filename.substring(dotIdx + 1) : "";
    }

    /** InputStream을 읽으면서 바이트 수를 카운트하는 DigestInputStream 래퍼 */
    private static class CountingDigestInputStream extends DigestInputStream {
        private long bytesRead = 0;

        CountingDigestInputStream(InputStream stream, MessageDigest digest) {
            super(stream, digest);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) bytesRead += n;
            return n;
        }

        long getBytesRead() { return bytesRead; }
    }
}
