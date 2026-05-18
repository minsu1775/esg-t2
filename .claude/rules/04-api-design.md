---
name: api-design
description: REST API 설계 규칙, OpenAPI 어노테이션, 예외 처리 핸들러 목록
paths:
  - "src/main/java/**/controller/**"
  - "src/main/java/**/api/**"
  - "src/main/java/**/rest/**"
  - "**/*Controller.java"
---

# API 설계 규칙

## OpenAPI 어노테이션 (전수 적용)

모든 REST 엔드포인트에 필수.

```java
@Operation(summary = "활동 데이터 등록", description = "Scope 1/2/3 활동 데이터를 등록하고 AuditLog를 기록합니다.")
@ApiResponse(responseCode = "201", description = "등록 성공")
@ApiResponse(responseCode = "400", description = "입력값 유효성 오류")
@ApiResponse(responseCode = "403", description = "권한 없음")
@PostMapping("/activity-data")
public ResponseEntity<ActivityDataResponse> create(
    @RequestBody @Valid CreateActivityDataRequest request
) { ... }
```

`@Schema`로 DTO 필드 설명 추가. 빌드 시 `docs/openapi.yml` 자동 생성·커밋.

## REST URL 규칙

- 복수 명사 사용: `/emission-records`, `/legal-entities`
- 동사 금지: `/calculate` → `/calculations` (또는 POST `/calculations`)
- 버전 prefix: `/api/v1/`
- 리소스 계층: `/api/v1/entities/{id}/emission-records`

## 응답 형식

```java
// 성공
ResponseEntity.status(201).body(response)   // 생성
ResponseEntity.ok(response)                 // 조회
ResponseEntity.noContent().build()          // 삭제/비활성화

// 페이지네이션
record PageResponse<T>(List<T> content, int page, int size, long totalElements) {}
```

## GlobalExceptionHandler 필수 핸들러

`@RestControllerAdvice` 클래스에 아래 핸들러 **모두** 등록. 누락 시 500 반환.

| 예외 | HTTP | 에러 코드 |
|---|---|---|
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `AccessDeniedException` | 403 | `ACCESS_DENIED` |
| `ObjectOptimisticLockingFailureException` | 409 | `OPTIMISTIC_LOCK_CONFLICT` |
| `ExpressionEvaluationException` | 400 | `FORMULA_EVALUATION_FAILED` |
| `FormulaValidationException` | 400 | `FORMULA_VALIDATION_FAILED` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` |
| `EsgException` | (코드별 매핑) | (코드 그대로) |

- `ExpressionEvaluationException` → 서비스 경계에서 `EsgException(FORMULA_EVALUATION_FAILED)`로 변환 후 도달.
- 에러 응답 형식: `{ "code": "...", "message": "한국어 메시지", "timestamp": "..." }`

## 요청 유효성 검사

```java
record CreateEntityRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull @Pattern(regexp = "[A-Z]{2}") String countryCode,
    @NotNull @Min(2020) @Max(2030) Integer reportingYear
) {}
```

Bean Validation 오류 → `MethodArgumentNotValidException` → 400 자동 변환.

## @PreAuthorize 필수

모든 컨트롤러 메서드에 인가 어노테이션 필수. 누락 허용 없음.

```java
@PreAuthorize("hasRole('ESG_MANAGER')")
@PostMapping(...)
```

## 트랜잭션 — 컨트롤러에 @Transactional 금지

컨트롤러에 `@Transactional` 직접 부착 금지. 트랜잭션 경계는 Service 메서드에만.
