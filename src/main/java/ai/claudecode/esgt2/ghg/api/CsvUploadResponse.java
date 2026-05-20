package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "CSV/Webhook 업로드 처리 결과")
public record CsvUploadResponse(
    @Schema(description = "전체 처리 행 수") int totalRows,
    @Schema(description = "성공 행 수") int successCount,
    @Schema(description = "건너뜀 행 수 (중복)") int skipCount,
    @Schema(description = "오류 행 수") int errorCount,
    @Schema(description = "오류·건너뜀 행 상세 목록") List<RowResult> nonSuccessRows
) {
    @Schema(description = "개별 행 결과")
    public record RowResult(
        @Schema(description = "파일 내 행 번호 (헤더=1, 첫 데이터=2)") int lineNumber,
        @Schema(description = "처리 상태: SUCCESS, SKIPPED, ERROR") String status,
        @Schema(description = "오류·건너뜀 사유") String message
    ) {}
}
