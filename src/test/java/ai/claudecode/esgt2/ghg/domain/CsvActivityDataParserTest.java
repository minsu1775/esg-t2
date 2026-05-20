package ai.claudecode.esgt2.ghg.domain;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvActivityDataParserTest {

    private static ByteArrayResource csv(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 유효한_3행_파싱() {
        var resource = csv("""
            reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years
            2025,SCOPE3_CAT1,ELECTRONICS,10000,KRW,KR,MANUAL,SPEND_BASED,
            2025,SCOPE3_CAT2,,500000,KRW,KR,API,,
            2025,SCOPE3_CAT11,TV,1000,unit,KR,MANUAL,,8
            """);

        List<CsvRow> rows = CsvActivityDataParser.parse(resource);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).category()).isEqualTo("SCOPE3_CAT1");
        assertThat(rows.get(0).quantity()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(rows.get(0).subCategory()).isEqualTo("ELECTRONICS");
        assertThat(rows.get(0).lifetimeYears()).isNull();
        assertThat(rows.get(1).subCategory()).isNull();
        assertThat(rows.get(2).lifetimeYears()).isEqualTo(8);
    }

    @Test
    void lineNumber는_헤더를_1로_데이터를_2부터_시작() {
        var resource = csv("""
            reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years
            2025,SCOPE3_CAT1,ELECTRONICS,10000,KRW,KR,MANUAL,,
            2025,SCOPE3_CAT2,,500000,KRW,KR,,,
            """);

        List<CsvRow> rows = CsvActivityDataParser.parse(resource);

        assertThat(rows.get(0).lineNumber()).isEqualTo(2);
        assertThat(rows.get(1).lineNumber()).isEqualTo(3);
    }

    @Test
    void 값_앞뒤_공백은_트림된다() {
        var resource = csv("""
            reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years
             2025 , SCOPE3_CAT1 , ELECTRONICS ,10000, KRW , KR ,MANUAL,,
            """);

        List<CsvRow> rows = CsvActivityDataParser.parse(resource);

        assertThat(rows.get(0).category()).isEqualTo("SCOPE3_CAT1");
        assertThat(rows.get(0).countryCode()).isEqualTo("KR");
        assertThat(rows.get(0).reportingYear()).isEqualTo(2025);
    }

    @Test
    void 헤더_제외_100행_모두_파싱된다() {
        var sb = new StringBuilder(
            "reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years\n");
        for (int i = 0; i < 100; i++) {
            sb.append("2025,SCOPE3_CAT1,ELECTRONICS,10000,KRW,KR,MANUAL,,\n");
        }

        List<CsvRow> rows = CsvActivityDataParser.parse(csv(sb.toString()));

        assertThat(rows).hasSize(100);
        assertThat(rows.get(0).lineNumber()).isEqualTo(2);
        assertThat(rows.get(99).lineNumber()).isEqualTo(101);
    }

    @Test
    void data_source_없으면_기본값_MANUAL() {
        var resource = csv("""
            reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years
            2025,SCOPE3_CAT1,,10000,KRW,KR,,,
            """);

        List<CsvRow> rows = CsvActivityDataParser.parse(resource);

        assertThat(rows.get(0).dataSource()).isEqualTo("MANUAL");
    }

    @Test
    void 필수_헤더_누락_시_예외() {
        assertThatThrownBy(() -> CsvActivityDataParser.parse(csv("not,a,valid\ncsv")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
