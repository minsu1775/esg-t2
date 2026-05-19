package ai.claudecode.esgt2.ghg.infra;

import ai.claudecode.esgt2.ghg.domain.EmissionFactor;
import ai.claudecode.esgt2.ghg.domain.EmissionFactorResolver;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DefaultEmissionFactorResolver implements EmissionFactorResolver {

    private final EmissionFactorRepository repository;

    @Override
    @Transactional(readOnly = true)
    public EmissionFactor resolveAt(String category, String subCategory, String countryCode, LocalDate date) {
        return repository.findActiveAt(category, subCategory, countryCode, date)
            .map(e -> new EmissionFactor(
                e.getId(), e.getSource(), e.getCategory(), e.getSubCategory(),
                e.getCountryCode(), e.getReportingYear(), e.getGwpSource(),
                e.getFactorValue(), e.getUnit(),
                e.getEffectiveFrom(), e.getEffectiveTo(), e.isActive()))
            .orElseThrow(() -> new EsgException(EsgErrorCode.EMISSION_FACTOR_NOT_FOUND,
                "배출계수를 찾을 수 없습니다: category=%s, subCategory=%s, countryCode=%s, date=%s"
                    .formatted(category, subCategory, countryCode, date)));
    }
}
