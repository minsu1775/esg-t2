package ai.claudecode.esgt2.ghg.domain;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.Resource;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CsvActivityDataParser {

    private static final Set<String> REQUIRED_HEADERS = Set.of(
        "reporting_year", "category", "quantity", "unit", "country_code");

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setTrim(true)
        .build();

    private CsvActivityDataParser() {}

    public static List<CsvRow> parse(Resource resource) {
        List<CsvRow> rows = new ArrayList<>();
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             var csvParser = FORMAT.parse(reader)) {

            var headerMap = csvParser.getHeaderMap();
            if (headerMap == null || !headerMap.keySet().containsAll(REQUIRED_HEADERS)) {
                throw new IllegalArgumentException("필수 헤더 누락: " + REQUIRED_HEADERS);
            }

            for (CSVRecord record : csvParser) {
                rows.add(new CsvRow(
                    (int) record.getParser().getCurrentLineNumber(),
                    parseInt(record.get("reporting_year")),
                    record.get("category"),
                    emptyToNull(record.get("sub_category")),
                    parseDecimal(record.get("quantity")),
                    record.get("unit"),
                    record.get("country_code"),
                    emptyToDefault(record.get("data_source"), "MANUAL"),
                    emptyToNull(record.get("data_quality")),
                    parseInteger(record.get("lifetime_years"))
                ));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("CSV 파싱 실패: " + e.getMessage(), e);
        }
        return rows;
    }

    private static int parseInt(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
    }

    private static Integer parseInteger(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String emptyToDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }
}
