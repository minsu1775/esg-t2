package ai.claudecode.esgt2.entity.domain;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

/**
 * 법인 간 지분 관계를 DAG(Directed Acyclic Graph)로 표현하는 도메인 서비스.
 * 생성 시 사이클을 탐지하며, 실질 소유율을 경로 곱으로 계산한다.
 */
public class EntityRelationshipGraph {

    private final Map<UUID, List<EntityRelationship>> adjacency;
    private final Set<UUID> allChildren;

    private EntityRelationshipGraph(Map<UUID, List<EntityRelationship>> adjacency, Set<UUID> allChildren) {
        this.adjacency = adjacency;
        this.allChildren = allChildren;
    }

    public static EntityRelationshipGraph of(List<EntityRelationship> relationships) {
        Map<UUID, List<EntityRelationship>> adjacency = new HashMap<>();
        Set<UUID> allChildren = new HashSet<>();

        for (EntityRelationship rel : relationships) {
            adjacency.computeIfAbsent(rel.parentId(), k -> new ArrayList<>()).add(rel);
            allChildren.add(rel.childId());
        }

        detectCycle(adjacency);
        return new EntityRelationshipGraph(adjacency, allChildren);
    }

    public List<UUID> directChildren(UUID parentId) {
        return adjacency.getOrDefault(parentId, List.of()).stream()
            .map(EntityRelationship::childId)
            .toList();
    }

    public Set<UUID> allDescendants(UUID rootId) {
        Set<UUID> visited = new LinkedHashSet<>();
        dfs(rootId, visited);
        visited.remove(rootId);
        return visited;
    }

    public BigDecimal effectiveOwnershipRatio(UUID fromId, UUID toId) {
        return findEffectiveRatio(fromId, toId, BigDecimal.ONE, new HashSet<>());
    }

    public Set<UUID> roots() {
        Set<UUID> allParents = adjacency.keySet();
        Set<UUID> roots = new HashSet<>(allParents);
        roots.removeAll(allChildren);
        return roots;
    }

    private void dfs(UUID nodeId, Set<UUID> visited) {
        if (!visited.add(nodeId)) return;
        for (EntityRelationship rel : adjacency.getOrDefault(nodeId, List.of())) {
            dfs(rel.childId(), visited);
        }
    }

    private BigDecimal findEffectiveRatio(UUID current, UUID target, BigDecimal accumulated, Set<UUID> path) {
        if (current.equals(target)) return accumulated;
        path.add(current);

        for (EntityRelationship rel : adjacency.getOrDefault(current, List.of())) {
            if (!path.contains(rel.childId())) {
                BigDecimal result = findEffectiveRatio(
                    rel.childId(), target,
                    accumulated.multiply(rel.ownershipRatio(), MathContext.DECIMAL128),
                    new HashSet<>(path));
                if (result.compareTo(BigDecimal.ZERO) > 0) return result;
            }
        }
        return BigDecimal.ZERO;
    }

    private static void detectCycle(Map<UUID, List<EntityRelationship>> adjacency) {
        Set<UUID> visited = new HashSet<>();
        Set<UUID> inStack = new HashSet<>();

        for (UUID node : adjacency.keySet()) {
            if (dfsCycleCheck(node, adjacency, visited, inStack)) {
                throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "법인 관계에 순환 참조가 존재합니다.");
            }
        }
    }

    private static boolean dfsCycleCheck(UUID node, Map<UUID, List<EntityRelationship>> adjacency,
                                          Set<UUID> visited, Set<UUID> inStack) {
        if (inStack.contains(node)) return true;
        if (visited.contains(node)) return false;

        visited.add(node);
        inStack.add(node);

        for (EntityRelationship rel : adjacency.getOrDefault(node, List.of())) {
            if (dfsCycleCheck(rel.childId(), adjacency, visited, inStack)) return true;
        }

        inStack.remove(node);
        return false;
    }
}
