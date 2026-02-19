package org.unlaxer.dsl;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ParserIrSchemaSampleConsistencyTest {

    @Test
    public void testValidSampleSatisfiesDraftSchemaContract() throws Exception {
        Map<String, Object> schema = loadSchema();
        Map<String, Object> sample = loadSample("valid-minimal.json");
        validateTopLevelContract(schema, sample);
        validateNodeContract(schema, sample);
        validateSpanOrder(sample);
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

    private static Map<String, Object> loadSchema() throws Exception {
        String json = Files.readString(Path.of("docs/schema/parser-ir-v1.draft.json"));
        return JsonTestUtil.parseObject(json);
    }

    private static Map<String, Object> loadSample(String name) throws Exception {
        String json = Files.readString(Path.of("src/test/resources/schema/parser-ir").resolve(name));
        return JsonTestUtil.parseObject(json);
    }
}
