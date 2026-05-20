package ai.claudecode.esgt2.ghg;

import ai.claudecode.esgt2.ghg.infra.EmissionFactorLoader;
import ai.claudecode.esgt2.ghg.infra.EmissionFactorRepository;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EmissionFactorLoaderTest extends AbstractIntegrationTest {

    @Autowired
    private EmissionFactorLoader loader;

    @Autowired
    private EmissionFactorRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM consolidated_emission_contributions");
        jdbcTemplate.execute("DELETE FROM consolidated_emission_records");
        jdbcTemplate.execute("DELETE FROM emission_records");
        jdbcTemplate.execute("DELETE FROM activity_data");
        jdbcTemplate.execute("DELETE FROM emission_factors");
    }

    @Test
    void 동일_파일_2회_로드_시_중복_없음() {
        var resource = new ClassPathResource("emission-factors/keei-2025.yaml");

        loader.loadFile(resource);
        long countAfterFirst = repository.count();

        loader.loadFile(resource);
        long countAfterSecond = repository.count();

        assertThat(countAfterFirst).isEqualTo(countAfterSecond);
        assertThat(countAfterFirst).isEqualTo(4); // keei-2025.yaml에 4개 항목
    }

    @Test
    void 값_수정_후_재로드_올바르게_업데이트() {
        var resource = new ClassPathResource("emission-factors/keei-2025.yaml");
        loader.loadFile(resource);

        // SCOPE2_ELECTRICITY 계수 직접 변경
        jdbcTemplate.execute(
            "UPDATE emission_factors SET factor_value = 0.5000 " +
            "WHERE category = 'SCOPE2_ELECTRICITY' AND source = 'KEEI'"
        );

        loader.loadFile(resource);

        // P1: 재로드 시 기존 계수 비활성화 + 새 계수 INSERT 방식 → 활성 계수 조회
        var updated = repository.findActiveBySourceAndCategoryAndSubCategoryAndCountryCodeAndReportingYear(
            "KEEI", "SCOPE2_ELECTRICITY", "GRID", "KR", 2025);

        assertThat(updated).isPresent();
        // 재로드 후 원래 값(0.4156)으로 복원
        assertThat(updated.get().getFactorValue()).isEqualByComparingTo(new BigDecimal("0.4156"));
    }

    @Test
    void KEEI_DEFRA_두_파일_로드_시_각각_등록() {
        loader.loadFile(new ClassPathResource("emission-factors/keei-2025.yaml"));
        loader.loadFile(new ClassPathResource("emission-factors/defra-2025.yaml"));

        long keeiCount = repository.findAll().stream()
            .filter(e -> "KEEI".equals(e.getSource())).count();
        long defraCount = repository.findAll().stream()
            .filter(e -> "DEFRA".equals(e.getSource())).count();

        assertThat(keeiCount).isEqualTo(4);
        assertThat(defraCount).isEqualTo(3);
    }
}
