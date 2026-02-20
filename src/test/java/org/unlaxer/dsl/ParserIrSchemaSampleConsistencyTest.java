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
    public void testInvalidScopeBalanceIsRejected() throws Exception {
        Map<String, Object> sample = loadSample("invalid-scope-balance.json");
        try {
            validateOptionalContracts(sample);
            fail("expected scope balance failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("scope balance"));
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
            validateScopeBalance(sample);
        }
    }

    private static void validateParentReferences(Map<String, Object> sample) {
        List<Object> nodes = JsonTestUtil.getArray(sample, "nodes");
        Set<String> ids = new HashSet<>();
        for (Object item : nodes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) item;
            ids.add(JsonTestUtil.getString(n, "id"));
        }
        for (Object item : nodes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) item;
            if (!n.containsKey("parentId")) {
                continue;
            }
            String parentId = JsonTestUtil.getString(n, "parentId");
            if (!ids.contains(parentId)) {
                throw new IllegalArgumentException("unknown parentId: " + parentId);
            }
        }
    }

    private static void validateDiagnosticsContract(Map<String, Object> sample) {
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
            JsonTestUtil.getObject(diagnostic, "span");
            JsonTestUtil.getString(diagnostic, "message");
            if (diagnostic.containsKey("related")) {
                List<Object> related = JsonTestUtil.getArray(diagnostic, "related");
                for (Object relItem : related) {
                    if (!(relItem instanceof Map<?, ?> relRaw)) {
                        throw new IllegalArgumentException("diagnostic related must be object");
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> relatedObj = (Map<String, Object>) relRaw;
                    JsonTestUtil.getObject(relatedObj, "span");
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

    private static Map<String, Object> loadSchema() throws Exception {
        String json = Files.readString(Path.of("docs/schema/parser-ir-v1.draft.json"));
        return JsonTestUtil.parseObject(json);
    }

    private static Map<String, Object> loadSample(String name) throws Exception {
        String json = Files.readString(Path.of("src/test/resources/schema/parser-ir").resolve(name));
        return JsonTestUtil.parseObject(json);
    }
}
