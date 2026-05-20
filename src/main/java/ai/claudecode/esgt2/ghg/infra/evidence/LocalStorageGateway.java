package ai.claudecode.esgt2.ghg.infra.evidence;

import ai.claudecode.esgt2.ghg.domain.ObjectStorageGateway;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 개발 환경용 로컬 파일 시스템 스토리지. 운영 환경은 MinIO/S3 구현체로 교체. */
@Component
@Slf4j
class LocalStorageGateway implements ObjectStorageGateway {

    private final Path storageRoot;

    LocalStorageGateway(@Value("${app.storage.root:${java.io.tmpdir}/esg-evidence}") String rootPath) {
        this.storageRoot = Paths.get(rootPath);
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            throw new EsgException(EsgErrorCode.INTERNAL_ERROR, "스토리지 루트 디렉터리 생성 실패: " + rootPath);
        }
    }

    @Override
    public String upload(InputStream data, String storedFilename, String mimeType) {
        Path target = resolveContained(storageRoot, storedFilename);
        try {
            Files.copy(data, target);
            log.debug("파일 저장 완료: {}", target);
            return "local://" + target.toAbsolutePath();
        } catch (IOException e) {
            throw new EsgException(EsgErrorCode.INTERNAL_ERROR, "파일 저장 실패: " + e.getMessage());
        }
    }

    // T-3B-17: 경로 순회 방어 (03-security.md, 10-evidence-files.md)
    Path resolveContained(Path root, String filename) {
        Path resolved = root.resolve(filename).normalize();
        if (!resolved.startsWith(root)) {
            throw new EsgException(EsgErrorCode.INVALID_FILE_PATH, "경로 순회 차단: " + filename);
        }
        return resolved;
    }
}
