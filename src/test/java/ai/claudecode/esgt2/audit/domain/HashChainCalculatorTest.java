package ai.claudecode.esgt2.audit.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HashChainCalculatorTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant FIXED_TIME = Instant.ofEpochMilli(1_700_000_000_000L);

    private AuditEvent sampleEvent() {
        return new AuditEvent(TENANT_ID, "ENTITY_CREATED", ACTOR_ID, ENTITY_ID, "EntityResponse", FIXED_TIME);
    }

    @Test
    void 첫_번째_항목_해시는_이전_해시_없이_계산된다() {
        String hash = HashChainCalculator.compute(sampleEvent(), null);
        assertThat(hash).hasSize(64);  // SHA-256 hex = 64 chars
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void 동일_이벤트와_이전_해시가_같으면_동일_해시가_생성된다() {
        AuditEvent event = sampleEvent();
        String hash1 = HashChainCalculator.compute(event, "abc123");
        String hash2 = HashChainCalculator.compute(event, "abc123");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void 이전_해시가_달라지면_현재_해시도_달라진다() {
        AuditEvent event = sampleEvent();
        String hash1 = HashChainCalculator.compute(event, "prevHash1");
        String hash2 = HashChainCalculator.compute(event, "prevHash2");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void canonicalPayload_저장_경로와_검증_경로가_동일_결과를_반환한다() {
        // T-2-14: 저장/검증 경로 모두 canonicalPayload() 단일 메서드 사용
        AuditEvent event = sampleEvent();
        var payload1 = HashChainCalculator.canonicalPayload(event);
        var payload2 = HashChainCalculator.canonicalPayload(event);
        assertThat(payload1).isEqualTo(payload2);
        assertThat(payload1.keySet()).containsExactly(
            "actorId", "entityId", "entityType", "eventType", "occurredAt", "tenantId"
        );
    }

    @Test
    void canonicalPayload_키_순서가_항상_정렬된다() {
        // TreeMap 보장: 필드 삽입 순서와 관계없이 키 정렬 → 직렬화 일관성
        AuditEvent event = sampleEvent();
        var payload = HashChainCalculator.canonicalPayload(event);
        var keys = payload.keySet().stream().toList();
        var sortedKeys = keys.stream().sorted().toList();
        assertThat(keys).isEqualTo(sortedKeys);
    }

    @Test
    void entityId_null이면_빈_문자열로_직렬화된다() {
        var event = new AuditEvent(TENANT_ID, "SOME_ACTION", ACTOR_ID, null, null, FIXED_TIME);
        var payload = HashChainCalculator.canonicalPayload(event);
        assertThat(payload.get("entityId")).isEqualTo("");
        assertThat(payload.get("entityType")).isEqualTo("");
    }

    @Test
    void 체인_연결_검증_세_항목_앞에서부터_순차_재계산이_일치한다() {
        // T-2-08: Hash Chain 무결성 검증 로직 시뮬레이션
        AuditEvent e1 = new AuditEvent(TENANT_ID, "EVENT_1", ACTOR_ID, ENTITY_ID, "Type", FIXED_TIME);
        AuditEvent e2 = new AuditEvent(TENANT_ID, "EVENT_2", ACTOR_ID, ENTITY_ID, "Type", FIXED_TIME.plusSeconds(1));
        AuditEvent e3 = new AuditEvent(TENANT_ID, "EVENT_3", ACTOR_ID, ENTITY_ID, "Type", FIXED_TIME.plusSeconds(2));

        String h1 = HashChainCalculator.compute(e1, null);
        String h2 = HashChainCalculator.compute(e2, h1);
        String h3 = HashChainCalculator.compute(e3, h2);

        // 재계산
        assertThat(HashChainCalculator.compute(e1, null)).isEqualTo(h1);
        assertThat(HashChainCalculator.compute(e2, h1)).isEqualTo(h2);
        assertThat(HashChainCalculator.compute(e3, h2)).isEqualTo(h3);
    }
}
