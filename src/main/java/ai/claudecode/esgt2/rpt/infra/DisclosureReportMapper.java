package ai.claudecode.esgt2.rpt.infra;

import ai.claudecode.esgt2.rpt.domain.DisclosureReport;
import ai.claudecode.esgt2.rpt.domain.ReportSection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public final class DisclosureReportMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DisclosureReportMapper() {}

    public static DisclosureReportJpaEntity toEntity(DisclosureReport domain) {
        String content = buildContent(domain.sections(), domain.emissionsByScope());
        return DisclosureReportJpaEntity.builder()
            .id(domain.id())
            .tenantId(domain.tenantId())
            .entityId(domain.entityId())
            .reportingYear(domain.reportingYear())
            .framework(domain.framework())
            .status(domain.status())
            .content(content)
            .generatedAt(domain.generatedAt())
            .submittedAt(domain.submittedAt())
            .approvedAt(domain.approvedAt())
            .approvedBy(domain.approvedBy())
            .rejectionReason(domain.rejectionReason())
            .build();
    }

    private static String buildContent(List<ReportSection> sections,
                                        Map<String, ?> emissionsByScope) {
        try {
            return MAPPER.writeValueAsString(Map.of(
                "sections", sections,
                "emissionsByScope", emissionsByScope
            ));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
