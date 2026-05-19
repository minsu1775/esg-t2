package ai.claudecode.esgt2.ghg.infra;

import java.util.List;

// Jackson YAML 파싱용 DTO (com.fasterxml.jackson.dataformat:jackson-dataformat-yaml 전이 의존성 사용)
record EmissionFactorYaml(
    String source,
    int reportingYear,
    String gwpSource,
    String effectiveFrom,
    String effectiveTo,
    List<EmissionFactorYamlEntry> factors
) {
    record EmissionFactorYamlEntry(
        String category,
        String subCategory,
        String countryCode,
        String factorValue,
        String unit
    ) {}
}
