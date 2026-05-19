package ai.claudecode.esgt2.audit.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

public class HashChainCalculator {

    private HashChainCalculator() {}

    public static String compute(AuditEvent event, String previousHash) {
        String canonical = toCanonicalString(canonicalPayload(event));
        String input = canonical + (previousHash != null ? previousHash : "GENESIS");
        return sha256(input);
    }

    public static Map<String, Object> canonicalPayload(AuditEvent event) {
        var map = new TreeMap<String, Object>();
        map.put("actorId", event.actorId().toString());
        map.put("entityId", event.entityId() != null ? event.entityId().toString() : "");
        map.put("entityType", event.entityType() != null ? event.entityType() : "");
        map.put("eventType", event.eventType());
        map.put("occurredAt", event.occurredAt().toEpochMilli());
        map.put("tenantId", event.tenantId().toString());
        return map;
    }

    private static String toCanonicalString(Map<String, Object> map) {
        var sb = new StringBuilder();
        map.forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        return sb.toString();
    }

    private static String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(64);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
