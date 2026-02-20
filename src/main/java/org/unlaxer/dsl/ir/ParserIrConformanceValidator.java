package org.unlaxer.dsl.ir;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight runtime contract checks for parser IR adapters.
 */
public final class ParserIrConformanceValidator {
    private ParserIrConformanceValidator() {}

    public static void validate(ParserIrDocument document) {
        Objects.requireNonNull(document, "document");
        Map<String, Object> payload = document.payload();

        String irVersion = readString(payload, "irVersion");
        if (irVersion.isBlank()) {
            throw new IllegalArgumentException("irVersion must not be blank");
        }
        String source = readString(payload, "source");
        if (source.trim().isEmpty()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        List<Object> nodes = readArray(payload, "nodes");
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must not be empty");
        }
        readArray(payload, "diagnostics");

        for (Object item : nodes) {
            if (!(item instanceof Map<?, ?> rawNode)) {
                throw new IllegalArgumentException("node must be object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) rawNode;
            readString(node, "id");
            readString(node, "kind");
            Map<String, Object> span = readObject(node, "span");
            long start = readLong(span, "start");
            long end = readLong(span, "end");
            if (start > end) {
                throw new IllegalArgumentException("span.start <= span.end required");
            }
        }
    }

    private static String readString(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException("missing or invalid string key: " + key);
        }
        return stringValue;
    }

    private static long readLong(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("missing or invalid number key: " + key);
    }

    private static List<Object> readArray(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        if (!(value instanceof List<?> arrayValue)) {
            throw new IllegalArgumentException("missing or invalid array key: " + key);
        }
        @SuppressWarnings("unchecked")
        List<Object> casted = (List<Object>) arrayValue;
        return casted;
    }

    private static Map<String, Object> readObject(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        if (!(value instanceof Map<?, ?> rawObject)) {
            throw new IllegalArgumentException("missing or invalid object key: " + key);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> casted = (Map<String, Object>) rawObject;
        return casted;
    }
}
