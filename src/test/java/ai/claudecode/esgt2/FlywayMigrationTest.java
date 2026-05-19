package ai.claudecode.esgt2;

import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tenants_테이블이_생성된다() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'tenants'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void disclosure_schedules_테이블이_생성된다() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'disclosure_schedules'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void disclosure_schedule_시드_데이터가_존재한다() {
        Integer migrationCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = true",
            Integer.class
        );
        assertThat(migrationCount).isEqualTo(1);
    }
}
