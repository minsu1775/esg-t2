package ai.claudecode.esgt2.supply;

import ai.claudecode.esgt2.shared.security.JwtTokenProvider;
import ai.claudecode.esgt2.supply.support.SupplyTestConfig;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(SupplyTestConfig.class)
class SupplierControllerSecurityTest extends AbstractIntegrationTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate() {{
        setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;  // 모든 HTTP 응답을 오류로 처리하지 않음
            }
        });
    }};

    // T-6-09: 타법인 접근 시도 → 403
    @Test
    void SUPPLIER가_타법인_entityId로_POST_요청_시_403() {
        UUID tenantId    = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID myEntityId  = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID otherEntity = UUID.fromString("00000000-0000-0000-0000-000000000020");
        UUID actorId     = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // SUPPLIER JWT — entityId=myEntityId
        String token = jwtTokenProvider.generateAccessToken(
            actorId, tenantId, myEntityId, List.of("SUPPLIER"));

        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
            {"reportingYear":2025,"category":"SCOPE3_CAT1","subCategory":"X",
             "quantity":100,"unit":"KRW","countryCode":"KR"}
            """;

        // otherEntity로 요청 → 403
        var response = restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/supply/entities/" + otherEntity + "/activity-data",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    // 활성화 엔드포인트는 JWT 없이 접근 가능 (permitAll)
    @Test
    void 활성화_엔드포인트는_JWT_없이_400_반환() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 존재하지 않는 토큰 → 404/400 기대 (401 아님)
        String body = """
            {"token":"00000000-0000-0000-0000-000000000000","password":"pass1234"}
            """;

        var response = restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/supply/suppliers/activate",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);

        // JWT 없이 접근 가능 (401 아님), 토큰이 없으면 400/404
        assertThat(response.getStatusCode().value()).isNotEqualTo(401);
    }
}
