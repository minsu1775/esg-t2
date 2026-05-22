package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.api.FormulaImpactResponse;
import ai.claudecode.esgt2.ghg.api.FormulaVersionResponse;
import ai.claudecode.esgt2.ghg.api.FormulaVersionService;
import ai.claudecode.esgt2.ghg.api.RegisterFormulaRequest;
import ai.claudecode.esgt2.ghg.domain.formula.FormulaLoader;
import ai.claudecode.esgt2.ghg.domain.formula.FormulaValidationException;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.ghg.infra.FormulaVersionJpaEntity;
import ai.claudecode.esgt2.ghg.infra.FormulaVersionRepository;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class DefaultFormulaVersionService implements FormulaVersionService {

    private final FormulaVersionRepository formulaVersionRepository;
    private final ActivityDataRepository activityDataRepository;

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Override
    @Transactional
    @Auditable(action = "FORMULA_VERSION_REGISTERED")
    public FormulaVersionResponse register(UUID actorId, RegisterFormulaRequest request) {
        // 1. test_cases 게이트 — 실패 시 FormulaValidationException(400) throw (T-6B-07)
        FormulaLoader.validate(request.yamlContent());

        // 2. YAML에서 메타데이터 추출
        try {
            var root = YAML_MAPPER.readTree(request.yamlContent());
            var formula = root.get("formula");
            String code    = formula.get("code").asText();
            String version = formula.get("version").asText();
            String expression = formula.get("expression").asText();
            String ghgCategory = formula.has("ghg_category")
                ? formula.get("ghg_category").asText(null) : null;

            // 3. 동일 code의 기존 ACTIVE 버전 비활성화 (최신 버전이 항상 하나만 ACTIVE)
            formulaVersionRepository.deactivateAllByCode(code);

            // 4. 새 버전 저장
            var entity = FormulaVersionJpaEntity.builder()
                .code(code).version(version).expression(expression)
                .ghgCategory(ghgCategory).yamlContent(request.yamlContent())
                .activatedBy(actorId)
                .build();
            return toResponse(formulaVersionRepository.save(entity));

        } catch (FormulaValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "YAML 파싱 오류: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FormulaVersionResponse> findAll(String code) {
        return formulaVersionRepository.findByCode(code).stream()
            .map(this::toResponse).toList();
    }

    @Override
    @Transactional
    @Auditable(action = "FORMULA_VERSION_DEACTIVATED")
    public FormulaVersionResponse deactivate(UUID actorId, UUID formulaVersionId) {
        var entity = formulaVersionRepository.findById(formulaVersionId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "formula_version not found: " + formulaVersionId));
        entity.deactivate();
        return toResponse(formulaVersionRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public FormulaImpactResponse getImpact(UUID tenantId, UUID formulaVersionId) {
        var entity = formulaVersionRepository.findById(formulaVersionId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "formula_version not found: " + formulaVersionId));
        if (entity.getGhgCategory() == null) {
            return new FormulaImpactResponse(
                entity.getCode(), entity.getVersion(), null, 0L, List.of());
        }
        var affected = activityDataRepository.findByTenantIdAndCategory(
            tenantId, entity.getGhgCategory());
        var entityIds = affected.stream()
            .map(ad -> ad.getEntityId())
            .distinct().toList();
        return new FormulaImpactResponse(
            entity.getCode(), entity.getVersion(), entity.getGhgCategory(),
            affected.size(), entityIds);
    }

    private FormulaVersionResponse toResponse(FormulaVersionJpaEntity e) {
        return new FormulaVersionResponse(
            e.getId(), e.getCode(), e.getVersion(),
            e.getExpression(), e.getGhgCategory(), e.getStatus(), e.getCreatedAt());
    }
}
