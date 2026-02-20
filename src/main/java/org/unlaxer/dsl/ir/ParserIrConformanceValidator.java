package org.unlaxer.dsl.ir;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;

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
        Map<String, Map<String, Object>> nodesById = new LinkedHashMap<>();
        for (Object item : nodes) {
            if (!(item instanceof Map<?, ?> rawNode)) {
                throw new IllegalArgumentException("node must be object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) rawNode;
            String nodeId = readString(node, "id");
            if (nodesById.put(nodeId, node) != null) {
                throw new IllegalArgumentException("duplicate node id: " + nodeId);
            }
            readString(node, "kind");
            Map<String, Object> span = readObject(node, "span");
            long start = readLong(span, "start");
            long end = readLong(span, "end");
            if (start > end) {
                throw new IllegalArgumentException("span.start <= span.end required");
            }
        }
        validateParentChildLinks(nodesById);
        validateAnnotationTargets(payload, nodesById.keySet());
    }

    private static void validateParentChildLinks(Map<String, Map<String, Object>> nodesById) {
        for (Map.Entry<String, Map<String, Object>> entry : nodesById.entrySet()) {
            String nodeId = entry.getKey();
            Map<String, Object> node = entry.getValue();
            if (node.containsKey("parentId")) {
                String parentId = readString(node, "parentId");
                if (!nodesById.containsKey(parentId)) {
                    throw new IllegalArgumentException("unknown parentId: " + parentId);
                }
                Map<String, Object> parent = nodesById.get(parentId);
                if (!parent.containsKey("children")) {
                    throw new IllegalArgumentException("missing parent children link: parent=" + parentId + " child=" + nodeId);
                }
                List<Object> children = readArray(parent, "children");
                if (!children.contains(nodeId)) {
                    throw new IllegalArgumentException("missing parent children link: parent=" + parentId + " child=" + nodeId);
                }
            }
            if (node.containsKey("children")) {
                List<Object> children = readArray(node, "children");
                Set<String> seen = new HashSet<>();
                for (Object childObj : children) {
                    if (!(childObj instanceof String childId) || childId.isBlank()) {
                        throw new IllegalArgumentException("invalid child id type");
                    }
                    if (!seen.add(childId)) {
                        throw new IllegalArgumentException("duplicate child id: " + childId);
                    }
                    if (!nodesById.containsKey(childId)) {
                        throw new IllegalArgumentException("unknown child id: " + childId);
                    }
                }
            }
        }
    }

    private static void validateAnnotationTargets(Map<String, Object> payload, Set<String> nodeIds) {
        if (!payload.containsKey("annotations")) {
            return;
        }
        List<Object> annotations = readArray(payload, "annotations");
        for (Object item : annotations) {
            if (!(item instanceof Map<?, ?> rawAnnotation)) {
                throw new IllegalArgumentException("annotation must be object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> annotation = (Map<String, Object>) rawAnnotation;
            String targetId = readString(annotation, "targetId");
            if (!nodeIds.contains(targetId)) {
                throw new IllegalArgumentException("unknown annotation targetId: " + targetId);
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
