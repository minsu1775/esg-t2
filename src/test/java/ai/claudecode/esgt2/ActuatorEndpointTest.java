package ai.claudecode.esgt2;

import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actuator 엔드포인트 통합 테스트.
 * TestContainers PostgreSQL(AbstractIntegrationTest)을 사용하여
 * DB 헬스체크가 올바르게 200을 반환함을 검증합니다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
        "management.health.redis.enabled=false",
        // 테스트 환경에서 메일 서버(localhost:1025)가 없으므로 MailHealthIndicator 비활성화
        "management.health.mail.enabled=false"
    }
)
class ActuatorEndpointTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void health_엔드포인트가_200을_반환한다() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .with(httpBasic("admin", "admin")))
            .andExpect(status().isOk());
    }

    @Test
    void prometheus_메트릭_엔드포인트가_200을_반환한다() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .with(httpBasic("admin", "admin")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }
}
