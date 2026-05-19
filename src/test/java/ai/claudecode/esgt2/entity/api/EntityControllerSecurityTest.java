package ai.claudecode.esgt2.entity.api;

import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntityControllerSecurityTest extends AbstractIntegrationTest {

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
    void 인증_없이_법인_생성_시_401이_반환된다() throws Exception {
        mockMvc.perform(post("/api/v1/entities")
                .contentType("application/json")
                .content("""
                    {"name":"테스트법인","countryCode":"KR","entityType":"PARENT"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ESG_VIEWER")
    void ESG_VIEWER_역할로_법인_생성_시_403이_반환된다() throws Exception {
        mockMvc.perform(post("/api/v1/entities")
                .contentType("application/json")
                .content("""
                    {"name":"테스트법인","countryCode":"KR","entityType":"PARENT"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void TENANT_ADMIN_역할로_법인_생성_시_403이_아니다() throws Exception {
        var result = mockMvc.perform(post("/api/v1/entities")
                .contentType("application/json")
                .content("""
                    {"name":"테스트법인","countryCode":"KR","entityType":"PARENT"}
                    """))
            .andReturn();

        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
    }
}
