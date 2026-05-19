package ai.claudecode.esgt2.entity;

import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTableMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Integer tableCount(String tableName) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
            Integer.class, tableName);
    }

    @Test
    void legal_entities_테이블이_생성된다() {
        assertThat(tableCount("legal_entities")).isEqualTo(1);
    }

    @Test
    void entity_relationships_테이블이_생성된다() {
        assertThat(tableCount("entity_relationships")).isEqualTo(1);
    }

    @Test
    void users_테이블이_생성된다() {
        assertThat(tableCount("users")).isEqualTo(1);
    }

    @Test
    void user_roles_테이블이_생성된다() {
        assertThat(tableCount("user_roles")).isEqualTo(1);
    }
}
