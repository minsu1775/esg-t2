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
import org.springframework.web.bind.annotation.PostMapping;
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

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void OptimisticLockingFailureException이_409로_반환된다() throws Exception {
        // @Auditable 컨트롤러가 없어도 /test/optimistic-lock 엔드포인트로 검증
        // 여기서는 standaloneSetup으로 직접 검증
        var exceptionController = new OptimisticLockTestController();
        var standaloneSetup = MockMvcBuilders.standaloneSetup(exceptionController)
            .setControllerAdvice(context.getBean(ai.claudecode.esgt2.shared.web.GlobalExceptionHandler.class))
            .build();

        standaloneSetup.perform(post("/test/optimistic-lock"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/test")
    static class OptimisticLockTestController {
        @PostMapping("/optimistic-lock")
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
        public String trigger() {
            throw new org.springframework.dao.OptimisticLockingFailureException("test conflict");
        }
    }
}
