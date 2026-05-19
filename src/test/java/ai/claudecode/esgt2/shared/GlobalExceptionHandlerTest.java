package ai.claudecode.esgt2.shared;

import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest extends AbstractIntegrationTest {

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
    @WithMockUser(roles = "ESG_VIEWER")
    void AccessDeniedException이_code_필드와_함께_403으로_반환된다() throws Exception {
        // ESG_VIEWER로 TENANT_ADMIN 전용 엔드포인트 호출 → AccessDeniedException
        mockMvc.perform(post("/api/v1/entities")
                .contentType("application/json")
                .content("""
                    {"name":"테스트법인","countryCode":"KR","entityType":"PARENT"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void MethodArgumentNotValidException이_400으로_반환된다() throws Exception {
        // 국가코드 소문자 → Bean Validation 실패
        mockMvc.perform(post("/api/v1/entities")
                .contentType("application/json")
                .content("""
                    {"name":"테스트법인","countryCode":"kr","entityType":"PARENT"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
