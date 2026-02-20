package org.unlaxer.dsl;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Test;

public class ParserIrSchemaSampleConsistencyTest {

    private static final Pattern DIAGNOSTIC_CODE_PATTERN = Pattern.compile("^E-[A-Z0-9-]+$|^W-[A-Z0-9-]+$|^I-[A-Z0-9-]+$");
    private static final Pattern ANNOTATION_NAME_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9-]*$");
    private static final Set<String> DIAGNOSTIC_SEVERITIES = Set.of("ERROR", "WARNING", "INFO");
    private static final Set<String> SCOPE_EVENTS = Set.of("enterScope", "leaveScope", "define", "use");

    @Test
    public void testValidSampleSatisfiesDraftSchemaContract() throws Exception {
        Map<String, Object> schema = loadSchema();
        Map<String, Object> sample = loadSample("valid-minimal.json");
        validateTopLevelContract(schema, sample);
        validateNodeContract(schema, sample);
        validateSpanOrder(sample);
        validateParentReferences(sample);
        validateOptionalContracts(sample);
    }

    @Test
    public void testValidRichSampleSatisfiesDraftSchemaContract() throws Exception {
        Map<String, Object> schema = loadSchema();
        Map<String, Object> sample = loadSample("valid-rich.json");
        validateTopLevelContract(schema, sample);
        validateNodeContract(schema, sample);
        validateSpanOrder(sample);
        validateParentReferences(sample);
        validateOptionalContracts(sample);
    }

    @Test
    public void testInvalidSampleMissingRequiredIsRejected() throws Exception {
        Map<String, Object> schema = loadSchema();
        Map<String, Object> sample = loadSample("invalid-missing-required.json");
        try {
            validateTopLevelContract(schema, sample);
            fail("expected missing required key failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("missing required key"));
        }
    }

    @Test
    public void testInvalidSampleSpanOrderIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-span-order.json");
        try {
            validateSpanOrder(sample);
            fail("expected span order failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("span.start <= span.end"));
        }
    }

    @Test
    public void testInvalidDiagnosticCodeIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-diagnostic-code.json");
        try {
            validateOptionalContracts(sample);
            fail("expected diagnostic code pattern failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("diagnostic code pattern"));
        }
    }

    @Test
    public void testInvalidScopeEventIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-scope-event.json");
        try {
            validateOptionalContracts(sample);
            fail("expected scope event failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unsupported scope event"));
        }
    }

    @Test
    public void testInvalidDiagnosticRelatedIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-related.json");
        try {
            validateOptionalContracts(sample);
            fail("expected diagnostic related failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("missing key"));
        }
    }

    @Test
    public void testInvalidDiagnosticRelatedSpanRangeIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-related-span-range.json");
        try {
            validateOptionalContracts(sample);
            fail("expected diagnostic related span range failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("diagnostic related span out of range"));
        }
    }

    @Test
    public void testInvalidParentIdReferenceIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-parent-id.json");
        try {
            validateParentReferences(sample);
            fail("expected parentId reference failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unknown parentId"));
        }
    }

    @Test
    public void testDuplicateNodeIdIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-duplicate-node-id.json");
        try {
            validateParentReferences(sample);
            fail("expected duplicate node id failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("duplicate node id"));
        }
    }

    @Test
    public void testInvalidChildIdReferenceIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-child-id.json");
        try {
            validateParentReferences(sample);
            fail("expected childId reference failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unknown child id"));
        }
    }

    @Test
    public void testChildSelfReferenceIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-child-self-reference.json");
        try {
            validateParentReferences(sample);
            fail("expected child self reference failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("child self reference"));
        }
    }

    @Test
    public void testInvalidScopeBalanceIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-scope-balance.json");
        try {
            validateOptionalContracts(sample);
            fail("expected scope balance failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("scope balance"));
        }
    }

    @Test
    public void testInvalidDiagnosticSpanRangeIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-diagnostic-span-range.json");
        try {
            validateOptionalContracts(sample);
            fail("expected diagnostic span range failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("diagnostic span out of range"));
        }
    }

    @Test
    public void testInvalidScopeEventOrderIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-scope-order.json");
        try {
            validateOptionalContracts(sample);
            fail("expected scope order failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("scope order violated"));
        }
    }

    @Test
    public void testInvalidTargetScopeIdIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-target-scope-id.json");
        try {
            validateOptionalContracts(sample);
            fail("expected target scope failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unknown targetScopeId"));
        }
    }

    @Test
    public void testDuplicateScopeEnterIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-duplicate-scope-enter.json");
        try {
            validateOptionalContracts(sample);
            fail("expected duplicate scope enter failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("duplicate enterScope"));
        }
    }

    @Test
    public void testInvalidAnnotationTargetIdIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-annotation-target-id.json");
        try {
            validateOptionalContracts(sample);
            fail("expected annotation target failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unknown annotation targetId"));
        }
    }

    @Test
    public void testInvalidAnnotationNameIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-annotation-name.json");
        try {
            validateOptionalContracts(sample);
            fail("expected annotation name failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("annotation name pattern"));
        }
    }

    @Test
    public void testDuplicateAnnotationNameOnSameTargetIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-annotation-duplicate-name.json");
        try {
            validateOptionalContracts(sample);
            fail("expected duplicate annotation failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("duplicate annotation"));
        }
    }

    @Test
    public void testEmptyAnnotationPayloadIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-annotation-empty-payload.json");
        try {
            validateOptionalContracts(sample);
            fail("expected empty payload failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("annotation payload must not be empty"));
        }
    }

    private static void validateTopLevelContract(Map<String, Object> schema, Map<String, Object> sample) {
        List<Object> required = JsonTestUtil.getArray(schema, "required");
        for (Object k : required) {
            String key = (String) k;
            if (!sample.containsKey(key)) {
                throw new IllegalArgumentException("missing required key: " + key);
            }
        }
        String irVersion = JsonTestUtil.getString(sample, "irVersion");
        if (!"1.0".equals(irVersion)) {
            throw new IllegalArgumentException("unsupported irVersion: " + irVersion);
        }
    }

    private static void validateNodeContract(Map<String, Object> schema, Map<String, Object> sample) {
        Map<String, Object> defs = JsonTestUtil.getObject(schema, "$defs");
        Map<String, Object> node = JsonTestUtil.getObject(defs, "node");
        List<Object> requiredNodeKeys = JsonTestUtil.getArray(node, "required");
        List<Object> nodes = JsonTestUtil.getArray(sample, "nodes");
        for (Object item : nodes) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("node must be object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) raw;
            for (Object keyObj : requiredNodeKeys) {
                String key = (String) keyObj;
                if (!n.containsKey(key)) {
                    throw new IllegalArgumentException("missing required node key: " + key);
                }
            }
        }
    }

    private static void validateSpanOrder(Map<String, Object> sample) {
        List<Object> nodes = JsonTestUtil.getArray(sample, "nodes");
        for (Object item : nodes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) item;
            Map<String, Object> span = JsonTestUtil.getObject(n, "span");
            long start = JsonTestUtil.getLong(span, "start");
            long end = JsonTestUtil.getLong(span, "end");
            if (start > end) {
                throw new IllegalArgumentException("span.start <= span.end required");
            }
        }
    }

    private static void validateOptionalContracts(Map<String, Object> sample) {
        if (sample.containsKey("diagnostics")) {
            validateDiagnosticsContract(sample);
        }
        if (sample.containsKey("scopeEvents")) {
            validateScopeEventsContract(sample);
            validateScopeEventOrder(sample);
            validateScopeBalance(sample);
            validateScopeTargetReferences(sample);
        }
        if (sample.containsKey("annotations")) {
            validateAnnotationTargets(sample);
        }
    }

    private static void validateParentReferences(Map<String, Object> sample) {
        List<Object> nodes = JsonTestUtil.getArray(sample, "nodes");
        Set<String> ids = new HashSet<>();
        for (Object item : nodes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) item;
            String id = JsonTestUtil.getString(n, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate node id: " + id);
            }
        }
        for (Object item : nodes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) item;
            String id = JsonTestUtil.getString(n, "id");
            if (n.containsKey("parentId")) {
                String parentId = JsonTestUtil.getString(n, "parentId");
                if (!ids.contains(parentId)) {
                    throw new IllegalArgumentException("unknown parentId: " + parentId);
                }
                if (id.equals(parentId)) {
                    throw new IllegalArgumentException("parent self reference: " + id);
                }
            }
            if (n.containsKey("children")) {
                List<Object> children = JsonTestUtil.getArray(n, "children");
                for (Object childObj : children) {
                    if (!(childObj instanceof String childId) || childId.isBlank()) {
                        throw new IllegalArgumentException("invalid child id type");
                    }
                    if (!ids.contains(childId)) {
                        throw new IllegalArgumentException("unknown child id: " + childId);
                    }
                    if (id.equals(childId)) {
                        throw new IllegalArgumentException("child self reference: " + id);
                    }
                }
            }
        }
    }

    private static void validateDiagnosticsContract(Map<String, Object> sample) {
        long minStart = Long.MAX_VALUE;
        long maxEnd = Long.MIN_VALUE;
        List<Object> nodes = JsonTestUtil.getArray(sample, "nodes");
        for (Object item : nodes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) item;
            Map<String, Object> nodeSpan = JsonTestUtil.getObject(n, "span");
            long start = JsonTestUtil.getLong(nodeSpan, "start");
            long end = JsonTestUtil.getLong(nodeSpan, "end");
            minStart = Math.min(minStart, start);
            maxEnd = Math.max(maxEnd, end);
        }

        List<Object> diagnostics = JsonTestUtil.getArray(sample, "diagnostics");
        for (Object item : diagnostics) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("diagnostic must be object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> diagnostic = (Map<String, Object>) raw;
            String code = JsonTestUtil.getString(diagnostic, "code");
            if (!DIAGNOSTIC_CODE_PATTERN.matcher(code).matches()) {
                throw new IllegalArgumentException("diagnostic code pattern mismatch: " + code);
            }
            String severity = JsonTestUtil.getString(diagnostic, "severity");
            if (!DIAGNOSTIC_SEVERITIES.contains(severity)) {
                throw new IllegalArgumentException("unsupported diagnostic severity: " + severity);
            }
            Map<String, Object> span = JsonTestUtil.getObject(diagnostic, "span");
            long start = JsonTestUtil.getLong(span, "start");
            long end = JsonTestUtil.getLong(span, "end");
            if (start < minStart || end > maxEnd) {
                throw new IllegalArgumentException(
                    "diagnostic span out of range: [" + start + "," + end + "] not in [" + minStart + "," + maxEnd + "]"
                );
            }
            JsonTestUtil.getString(diagnostic, "message");
            if (diagnostic.containsKey("related")) {
                List<Object> related = JsonTestUtil.getArray(diagnostic, "related");
                for (Object relItem : related) {
                    if (!(relItem instanceof Map<?, ?> relRaw)) {
                        throw new IllegalArgumentException("diagnostic related must be object");
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> relatedObj = (Map<String, Object>) relRaw;
                    Map<String, Object> relSpan = JsonTestUtil.getObject(relatedObj, "span");
                    long relStart = JsonTestUtil.getLong(relSpan, "start");
                    long relEnd = JsonTestUtil.getLong(relSpan, "end");
                    if (relStart < minStart || relEnd > maxEnd) {
                        throw new IllegalArgumentException(
                            "diagnostic related span out of range: ["
                                + relStart + "," + relEnd + "] not in [" + minStart + "," + maxEnd + "]"
                        );
                    }
                    JsonTestUtil.getString(relatedObj, "message");
                }
            }
        }
    }

    private static void validateScopeEventsContract(Map<String, Object> sample) {
        List<Object> scopeEvents = JsonTestUtil.getArray(sample, "scopeEvents");
        for (Object item : scopeEvents) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("scopeEvent must be object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) raw;
            String eventName = JsonTestUtil.getString(event, "event");
            if (!SCOPE_EVENTS.contains(eventName)) {
                throw new IllegalArgumentException("unsupported scope event: " + eventName);
            }
            JsonTestUtil.getString(event, "scopeId");
            JsonTestUtil.getObject(event, "span");
        }
    }

    private static void validateScopeBalance(Map<String, Object> sample) {
        List<Object> scopeEvents = JsonTestUtil.getArray(sample, "scopeEvents");
        Set<String> openScopes = new HashSet<>();
        for (Object item : scopeEvents) {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) item;
            String eventName = JsonTestUtil.getString(event, "event");
            String scopeId = JsonTestUtil.getString(event, "scopeId");
            if ("enterScope".equals(eventName)) {
                if (openScopes.contains(scopeId)) {
                    throw new IllegalArgumentException("duplicate enterScope for scopeId: " + scopeId);
                }
                openScopes.add(scopeId);
                continue;
            }
            if ("leaveScope".equals(eventName)) {
                if (!openScopes.remove(scopeId)) {
                    throw new IllegalArgumentException("scope balance violated for scopeId: " + scopeId);
                }
            }
        }
        if (!openScopes.isEmpty()) {
            throw new IllegalArgumentException("scope balance violated: unclosed scopes " + openScopes);
        }
    }

    private static void validateScopeEventOrder(Map<String, Object> sample) {
        List<Object> scopeEvents = JsonTestUtil.getArray(sample, "scopeEvents");
        Set<String> openScopes = new HashSet<>();
        for (Object item : scopeEvents) {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) item;
            String eventName = JsonTestUtil.getString(event, "event");
            String scopeId = JsonTestUtil.getString(event, "scopeId");
            if ("enterScope".equals(eventName)) {
                openScopes.add(scopeId);
                continue;
            }
            if ("define".equals(eventName) || "use".equals(eventName)) {
                if (!openScopes.contains(scopeId)) {
                    throw new IllegalArgumentException("scope order violated for scopeId: " + scopeId + " event=" + eventName);
                }
            }
            if ("leaveScope".equals(eventName)) {
                openScopes.remove(scopeId);
            }
        }
    }

    private static void validateScopeTargetReferences(Map<String, Object> sample) {
        List<Object> scopeEvents = JsonTestUtil.getArray(sample, "scopeEvents");
        Set<String> knownScopeIds = new HashSet<>();
        for (Object item : scopeEvents) {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) item;
            knownScopeIds.add(JsonTestUtil.getString(event, "scopeId"));
        }
        for (Object item : scopeEvents) {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) item;
            String eventName = JsonTestUtil.getString(event, "event");
            if (!"use".equals(eventName) || !event.containsKey("targetScopeId")) {
                continue;
            }
            String targetScopeId = JsonTestUtil.getString(event, "targetScopeId");
            if (!knownScopeIds.contains(targetScopeId)) {
                throw new IllegalArgumentException("unknown targetScopeId: " + targetScopeId);
            }
        }
    }

    private static void validateAnnotationTargets(Map<String, Object> sample) {
        List<Object> nodes = JsonTestUtil.getArray(sample, "nodes");
        Set<String> nodeIds = new HashSet<>();
        for (Object item : nodes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) item;
            nodeIds.add(JsonTestUtil.getString(node, "id"));
        }
        List<Object> annotations = JsonTestUtil.getArray(sample, "annotations");
        Set<String> seen = new HashSet<>();
        for (Object item : annotations) {
            @SuppressWarnings("unchecked")
            Map<String, Object> annotation = (Map<String, Object>) item;
            String targetId = JsonTestUtil.getString(annotation, "targetId");
            if (!nodeIds.contains(targetId)) {
                throw new IllegalArgumentException("unknown annotation targetId: " + targetId);
            }
            String name = JsonTestUtil.getString(annotation, "name");
            if (!ANNOTATION_NAME_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException("annotation name pattern mismatch: " + name);
            }
            Map<String, Object> payload = JsonTestUtil.getObject(annotation, "payload");
            if (payload.isEmpty()) {
                throw new IllegalArgumentException("annotation payload must not be empty");
            }
            String key = targetId + "\u0000" + name;
            if (!seen.add(key)) {
                throw new IllegalArgumentException("duplicate annotation for targetId=" + targetId + " name=" + name);
            }
        }
    }

    private static Map<String, Object> loadSchema() throws Exception {
        String json = Files.readString(Path.of("docs/schema/parser-ir-v1.draft.json"));
        return JsonTestUtil.parseObject(json);
    }

    private static Map<String, Object> loadSample(String name) throws Exception {
        String json = Files.readString(Path.of("src/test/resources/schema/parser-ir").resolve(name));
        return JsonTestUtil.parseObject(json);
    }
}
