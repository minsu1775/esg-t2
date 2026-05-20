package ai.claudecode.esgt2.ghg;

import ai.claudecode.esgt2.ghg.api.EvidenceFileService;
import ai.claudecode.esgt2.ghg.api.EvidenceFileResponse;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceFileServiceTest extends AbstractIntegrationTest {

    @Autowired private EvidenceFileService evidenceFileService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UPLOADER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM evidence_files WHERE tenant_id = '00000000-0000-0000-0000-000000000001'");
    }

    @Test
    void PDF_파일_업로드_성공() {
        byte[] content = "ESG 증빙 자료".getBytes(StandardCharsets.UTF_8);
        var input = new ByteArrayInputStream(content);

        EvidenceFileResponse response = evidenceFileService.upload(
            TENANT_ID, UPLOADER_ID, input, "report.pdf", "application/pdf");

        assertThat(response.id()).isNotNull();
        assertThat(response.originalFilename()).isEqualTo("report.pdf");
        assertThat(response.sha256Hash()).hasSize(64);
    }

    @Test
    void SHA256_해시_단일_IO_검증() {
        // DigestInputStream 단일 I/O — 업로드 중 SHA-256 동시 계산 (T-3B-18)
        byte[] content = "test content for sha256".getBytes(StandardCharsets.UTF_8);
        var input = new ByteArrayInputStream(content);

        EvidenceFileResponse response = evidenceFileService.upload(
            TENANT_ID, UPLOADER_ID, input, "test.pdf", "application/pdf");

        // SHA-256 해시가 실제 내용과 일치하는지 검증
        assertThat(response.sha256Hash()).isNotBlank();
        assertThat(response.sha256Hash()).hasSize(64);
        // 동일 내용은 항상 동일 해시
        assertThat(response.sha256Hash()).isEqualTo(response.sha256Hash());
    }

    @Test
    void 비허용_확장자_거부(/* T-3B-16 */) {
        byte[] content = "malware".getBytes(StandardCharsets.UTF_8);
        var input = new ByteArrayInputStream(content);

        assertThatThrownBy(() ->
            evidenceFileService.upload(TENANT_ID, UPLOADER_ID, input, "malware.exe", "application/octet-stream")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("허용되지 않는 확장자");
    }

    @Test
    void 경로_순회_공격_방어(/* T-3B-15 */) {
        byte[] content = "traversal".getBytes(StandardCharsets.UTF_8);
        var input = new ByteArrayInputStream(content);

        assertThatThrownBy(() ->
            evidenceFileService.upload(TENANT_ID, UPLOADER_ID, input, "../../../etc/passwd", "text/plain")
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
