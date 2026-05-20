package ai.claudecode.esgt2.ghg.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmissionFactorLoader {

    private final EmissionFactorRepository repository;

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @EventListener(ApplicationReadyEvent.class)
    public void loadAll() {
        var resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath:emission-factors/*.yaml");
            for (Resource resource : resources) {
                loadFile(resource);
            }
        } catch (Exception e) {
            log.warn("배출계수 YAML 로드 실패: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void loadFile(Resource resource) {
        try {
            var yaml = YAML_MAPPER.readValue(resource.getInputStream(), EmissionFactorYaml.class);
            log.info("배출계수 로드 시작: source={}, year={}, items={}", yaml.source(), yaml.reportingYear(), yaml.factors().size());
            for (var entry : yaml.factors()) {
                upsert(yaml, entry);
            }
            log.info("배출계수 로드 완료: source={}", yaml.source());
        } catch (Exception e) {
            log.warn("배출계수 파일 처리 중 오류: file={}, error={}", resource.getFilename(), e.getMessage(), e);
        }
    }

    private void upsert(EmissionFactorYaml yaml, EmissionFactorYaml.EmissionFactorYamlEntry entry) {
        var existing = repository.findActiveBySourceAndCategoryAndSubCategoryAndCountryCodeAndReportingYear(
            yaml.source(), entry.category(), entry.subCategory(),
            entry.countryCode(), yaml.reportingYear());

        LocalDate yamlEffectiveFrom = LocalDate.parse(yaml.effectiveFrom());

        // P1 재현성: UPDATE 금지 — 기존 계수 비활성화 후 새 계수 INSERT (06-emission-calculation.md)
        if (existing.isPresent()) {
            var oldEntity = existing.get();
            if (oldEntity.getFactorValue().compareTo(new BigDecimal(entry.factorValue())) == 0
                    && oldEntity.getUnit().equals(entry.unit())) {
                log.debug("배출계수 변경 없음, 스킵: source={}, category={}", yaml.source(), entry.category());
                return;
            }
            LocalDate today = LocalDate.now();
            oldEntity.deactivate(today.minusDays(1));
            repository.save(oldEntity);
            log.debug("기존 배출계수 비활성화: source={}, category={}, sub={}", yaml.source(), entry.category(), entry.subCategory());

            // 새 레코드의 effective_from = 오늘 (기존 비활성화 레코드와 unique key 충돌 방지)
            repository.save(EmissionFactorJpaEntity.builder()
                .id(UUID.randomUUID())
                .source(yaml.source())
                .category(entry.category())
                .subCategory(entry.subCategory())
                .countryCode(entry.countryCode())
                .reportingYear(yaml.reportingYear())
                .gwpSource(yaml.gwpSource())
                .factorValue(new BigDecimal(entry.factorValue()))
                .unit(entry.unit())
                .effectiveFrom(today)
                .effectiveTo(yaml.effectiveTo() != null ? LocalDate.parse(yaml.effectiveTo()) : null)
                .build());
            log.info("배출계수 갱신 완료 (이전값 비활성화): source={}, category={}, sub={}", yaml.source(), entry.category(), entry.subCategory());
            return;
        }

        repository.save(EmissionFactorJpaEntity.builder()
            .id(UUID.randomUUID())
            .source(yaml.source())
            .category(entry.category())
            .subCategory(entry.subCategory())
            .countryCode(entry.countryCode())
            .reportingYear(yaml.reportingYear())
            .gwpSource(yaml.gwpSource())
            .factorValue(new BigDecimal(entry.factorValue()))
            .unit(entry.unit())
            .effectiveFrom(yamlEffectiveFrom)
            .effectiveTo(yaml.effectiveTo() != null ? LocalDate.parse(yaml.effectiveTo()) : null)
            .build());
        log.debug("배출계수 신규 등록: source={}, category={}, sub={}", yaml.source(), entry.category(), entry.subCategory());
    }
}
