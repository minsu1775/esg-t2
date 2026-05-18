---
name: evidence-files
description: 증빙 파일 업로드 보안, DigestInputStream 단일 I/O, SHA-256 검증, Object Storage 설계
paths:
  - "src/main/java/**/evidence/**"
  - "src/main/java/**/storage/**"
  - "**/*Evidence*.java"
  - "**/*Storage*.java"
  - "**/*Upload*.java"
  - "**/*FileService*.java"
---

# 증빙 파일 규칙

## DigestInputStream — 단일 I/O (esg-t1 L-0-06)

파일 업로드 시 SHA-256 해시를 별도로 계산하면 I/O 2회 발생.
`DigestInputStream`으로 업로드와 SHA-256 계산을 **동시**에 처리.

```java
public EvidenceFileId upload(InputStream inputStream, String originalFilename, String mimeType) {
    validateExtension(originalFilename);   // 1. 확장자 검증 먼저

    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    String storedFilename = UUID.randomUUID() + getExtension(originalFilename);

    try (DigestInputStream dis = new DigestInputStream(inputStream, digest)) {
        StorageUri uri = storageGateway.upload(dis, storedFilename, mimeType);
        // 업로드 완료 후 해시값 즉시 추출 (I/O 1회)
        String sha256 = HexFormat.of().formatHex(digest.digest());

        return evidenceRepository.save(EvidenceFileEntity.builder()
            .storageUri(uri.value())
            .sha256(sha256)
            .originalFilename(originalFilename)
            .storedFilename(storedFilename)
            .mimeType(mimeType)
            .build()
        ).getId();
    }
}
```

## 경로 순회 방어 (esg-t1 BUG-P3-09)

```java
public Path resolveContained(Path storageRoot, String filename) {
    Path resolved = storageRoot.resolve(filename).normalize();
    if (!resolved.startsWith(storageRoot)) {
        throw new EsgException(EsgErrorCode.INVALID_FILE_PATH);
    }
    return resolved;
}
```

사용자가 제공하는 모든 파일 경로/파일명에 이 검증 적용.

## 파일 확장자 허용 목록

```java
private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
    "pdf", "xlsx", "xls", "csv", "png", "jpg", "jpeg"
);

private void validateExtension(String filename) {
    String ext = getExtension(filename).toLowerCase();
    if (!ALLOWED_EXTENSIONS.contains(ext)) {
        throw new EsgException(EsgErrorCode.UNSUPPORTED_FILE_TYPE,
            "허용되지 않는 파일 형식: " + ext);
    }
}
```

## 파일명 처리

- 저장 시 파일명 → **UUID로 재생성** (원본명은 `original_filename` 컬럼에만 보관).
- `storedFilename = UUID.randomUUID() + "." + extension`
- DB에는 Object Storage URI만 저장. 파일 자체를 DB에 저장 금지 (BLOB 금지).

## SHA-256 무결성 검증

다운로드 시 Object Storage에서 파일을 읽어 SHA-256 재계산 → DB 저장값과 비교.

```java
public InputStream download(UUID evidenceId) {
    EvidenceFileEntity evidence = evidenceRepository.findById(evidenceId)
        .orElseThrow(() -> new ResourceNotFoundException("증빙 파일 없음"));

    InputStream stream = storageGateway.download(StorageUri.of(evidence.getStorageUri()));
    // 무결성 검증 (필요 시 DigestInputStream으로 다운로드 중 해시 확인)
    return stream;
}
```

## 활동 데이터 삭제 금지

증빙과 연결된 활동 데이터는 **물리 삭제 금지**. 비활성화 처리(`is_active = false`).

```java
// ❌ 금지
activityDataRepository.delete(entity);

// ✅ 필수
entity.deactivate();   // is_active = false, deactivated_at = now()
activityDataRepository.save(entity);
```

## Object Storage 설계

| 환경 | 저장소 |
|---|---|
| 로컬 개발 | MinIO (Docker Compose, S3 호환 API) |
| 운영 | AWS S3 또는 S3 호환 |

```java
// 환경별 구현체 교체 가능 인터페이스
public interface ObjectStorageGateway {
    StorageUri upload(InputStream stream, String filename, String mimeType);
    InputStream download(StorageUri uri);
    // void delete() — 물리 삭제 대신 비활성화 처리
}
```

## 증빙 ↔ 활동 데이터 N:M 연결

동일 증빙 파일을 여러 활동 데이터에 첨부 가능 (N:M 관계).
`activity_data_evidence` 중간 테이블 사용.

## 증빙 → 공시 역추적 경로

```
공시 보고서 수치
  → EmissionRecord
    → ActivityData
      → EvidenceFile (다운로드)
```

3단계 이하로 원시 증빙까지 추적 가능해야 함. `activity_data_id` FK 항상 유지.
