package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.entity.api.ConsolidationMethod;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.entity.api.EntityRelationship;
import ai.claudecode.esgt2.entity.api.EntityRelationshipGraph;
import ai.claudecode.esgt2.entity.api.RelationshipResponse;
import ai.claudecode.esgt2.ghg.api.ConsolidationItemResponse;
import ai.claudecode.esgt2.ghg.api.ConsolidationResponse;
import ai.claudecode.esgt2.ghg.api.ConsolidationService;
import ai.claudecode.esgt2.ghg.domain.ConsolidationEngine;
import ai.claudecode.esgt2.ghg.domain.ConsolidationResult;
import ai.claudecode.esgt2.ghg.infra.ConsolidatedEmissionContributionJpaEntity;
import ai.claudecode.esgt2.ghg.infra.ConsolidatedEmissionContributionRepository;
import ai.claudecode.esgt2.ghg.infra.ConsolidatedEmissionRecordJpaEntity;
import ai.claudecode.esgt2.ghg.infra.ConsolidatedEmissionRecordRepository;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordRepository;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class DefaultConsolidationService implements ConsolidationService {

    private final EntityManagementService entityManagementService;
    private final EmissionRecordRepository emissionRecordRepository;
    private final ConsolidatedEmissionRecordRepository consolidatedRecordRepository;
    private final ConsolidatedEmissionContributionRepository contributionRepository;

    @Override
    @Transactional
    @Auditable(action = "CONSOLIDATION_CALCULATED")
    public ConsolidationResponse consolidate(UUID tenantId, UUID rootEntityId, int reportingYear, String method) {
        ConsolidationMethod consolidationMethod = parseMethod(method);

        List<EntityRelationship> rels = entityManagementService.findRelationships(tenantId).stream()
            .map(r -> toEntityRelationship(r, tenantId))
            .toList();
        EntityRelationshipGraph graph = EntityRelationshipGraph.of(rels);

        Set<UUID> entityIds = new HashSet<>(graph.allDescendants(rootEntityId));
        entityIds.add(rootEntityId);

        Map<UUID, BigDecimal> directEmissions = buildDirectEmissions(tenantId, entityIds, reportingYear);

        ConsolidationResult result = consolidationMethod == ConsolidationMethod.EQUITY
            ? ConsolidationEngine.consolidateEquity(rootEntityId, directEmissions, graph)
            : ConsolidationEngine.consolidateOperationalControl(rootEntityId, directEmissions, graph);

        UUID recordId = UUID.randomUUID();
        var savedRecord = consolidatedRecordRepository.save(
            ConsolidatedEmissionRecordJpaEntity.builder()
                .id(recordId)
                .tenantId(tenantId)
                .rootEntityId(rootEntityId)
                .reportingYear(reportingYear)
                .scope("ALL")
                .ghgType("CO2E")
                .consolidationMethod(method)
                .totalEmission(result.totalConsolidatedEmission())
                .build());

        List<ConsolidationItemResponse> contributions = persistContributions(
            recordId, rootEntityId, graph, result.entityContributions());

        return toResponse(savedRecord, contributions);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsolidationResponse> findConsolidations(UUID tenantId, UUID rootEntityId, int reportingYear) {
        return consolidatedRecordRepository
            .findByTenantIdAndRootEntityIdAndReportingYear(tenantId, rootEntityId, reportingYear)
            .stream()
            .map(r -> {
                List<ConsolidationItemResponse> contributions =
                    contributionRepository.findByConsolidatedRecordId(r.getId()).stream()
                        .map(c -> new ConsolidationItemResponse(
                            c.getEntityId(), c.getOwnershipRatio(), c.getWeightedEmission()))
                        .toList();
                return toResponse(r, contributions);
            })
            .toList();
    }

    private Map<UUID, BigDecimal> buildDirectEmissions(UUID tenantId, Set<UUID> entityIds, int reportingYear) {
        Map<UUID, BigDecimal> directEmissions = new HashMap<>();
        for (UUID entityId : entityIds) {
            BigDecimal total = emissionRecordRepository
                .findByTenantIdAndEntityIdAndReportingYear(tenantId, entityId, reportingYear)
                .stream()
                .map(e -> e.getRawEmission())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            directEmissions.put(entityId, total);
        }
        return directEmissions;
    }

    private List<ConsolidationItemResponse> persistContributions(UUID recordId, UUID rootEntityId,
            EntityRelationshipGraph graph, Map<UUID, BigDecimal> entityContributions) {
        List<ConsolidationItemResponse> items = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : entityContributions.entrySet()) {
            UUID entityId = entry.getKey();
            BigDecimal ratio = entityId.equals(rootEntityId)
                ? BigDecimal.ONE
                : graph.effectiveOwnershipRatio(rootEntityId, entityId);

            contributionRepository.save(
                ConsolidatedEmissionContributionJpaEntity.builder()
                    .consolidatedRecordId(recordId)
                    .entityId(entityId)
                    .ownershipRatio(ratio)
                    .weightedEmission(entry.getValue())
                    .build());
            items.add(new ConsolidationItemResponse(entityId, ratio, entry.getValue()));
        }
        return items;
    }

    private static EntityRelationship toEntityRelationship(RelationshipResponse r, UUID tenantId) {
        return new EntityRelationship(r.id(), tenantId, r.parentId(), r.childId(),
            r.ownershipRatio(), r.method(), r.effectiveFrom(), r.effectiveTo());
    }

    private static ConsolidationMethod parseMethod(String method) {
        try {
            return ConsolidationMethod.valueOf(method);
        } catch (IllegalArgumentException e) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED,
                "지원하지 않는 연결 방법입니다: " + method + " (EQUITY 또는 OPERATIONAL_CONTROL)");
        }
    }

    private static ConsolidationResponse toResponse(ConsolidatedEmissionRecordJpaEntity r,
            List<ConsolidationItemResponse> contributions) {
        return new ConsolidationResponse(
            r.getId(), r.getRootEntityId(), r.getReportingYear(),
            r.getScope(), r.getGhgType(), r.getConsolidationMethod(),
            r.getTotalEmission(), contributions, r.getCreatedAt());
    }
}
