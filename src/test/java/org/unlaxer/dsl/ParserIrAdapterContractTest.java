package org.unlaxer.dsl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.unlaxer.dsl.ir.ParseRequest;
import org.unlaxer.dsl.ir.ParserIrAdapter;
import org.unlaxer.dsl.ir.ParserIrAdapterMetadata;
import org.unlaxer.dsl.ir.ParserIrConformanceValidator;
import org.unlaxer.dsl.ir.ParserIrDocument;
import org.unlaxer.dsl.ir.ParserIrFeature;
import org.unlaxer.dsl.ir.ParserIrScopeEvents;

public class ParserIrAdapterContractTest {

    @Test
    public void testAdapterCanReturnConformantParserIr() throws Exception {
        ParserIrAdapter adapter = new FixtureBackedAdapter("valid-minimal.json");
        ParseRequest request = new ParseRequest("fixture://valid-minimal", "let a = 1;", Map.of());

        ParserIrAdapterMetadata metadata = adapter.metadata();
        assertEquals("fixture-adapter", metadata.adapterId());
        assertTrue(metadata.supportedIrVersions().contains("1.0"));

        ParserIrDocument document = adapter.parseToIr(request);
        ParserIrConformanceValidator.validate(document);
    }

    @Test
    public void testConformanceRejectsInvalidParserIrFixture() throws Exception {
        ParserIrAdapter adapter = new FixtureBackedAdapter("invalid-source-blank.json");
        ParseRequest request = new ParseRequest("fixture://invalid-source-blank", "let a = 1;", Map.of());

        try {
            ParserIrConformanceValidator.validate(adapter.parseToIr(request));
            fail("expected source blank failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("source must not be blank"));
        }
    }

    @Test
    public void testScopeTreeSampleAdapterBuildsConformantScopeEvents() {
        ParserIrAdapter adapter = new ScopeTreeSampleAdapter();
        ParseRequest request = new ParseRequest("sample://scope-tree", "ok", Map.of("scopeMode", "dynamic"));

        ParserIrDocument document = adapter.parseToIr(request);
        ParserIrConformanceValidator.validate(document);

        List<Object> scopeEvents = JsonTestUtil.getArray(document.payload(), "scopeEvents");
        assertEquals(2, scopeEvents.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) scopeEvents.get(0);
        assertEquals("enterScope", first.get("event"));
        assertEquals("dynamic", first.get("scopeMode"));
    }

    private static final class FixtureBackedAdapter implements ParserIrAdapter {
        private final String fixtureName;

        private FixtureBackedAdapter(String fixtureName) {
            this.fixtureName = fixtureName;
        }

        @Override
        public ParserIrAdapterMetadata metadata() {
            return new ParserIrAdapterMetadata(
                "fixture-adapter",
                Set.of("1.0"),
                Set.of(ParserIrFeature.ANNOTATIONS, ParserIrFeature.DIAGNOSTICS, ParserIrFeature.SCOPE_EVENTS)
            );
        }

        @Override
        public ParserIrDocument parseToIr(ParseRequest request) {
            if (request.content().isBlank()) {
                throw new IllegalArgumentException("content must not be blank");
            }
            try {
                String json = Files.readString(Path.of("src/test/resources/schema/parser-ir").resolve(fixtureName));
                return new ParserIrDocument(JsonTestUtil.parseObject(json));
            } catch (Exception e) {
                throw new IllegalStateException("failed to load fixture: " + fixtureName, e);
            }
        }
    }

    private static final class ScopeTreeSampleAdapter implements ParserIrAdapter {
        @Override
        public ParserIrAdapterMetadata metadata() {
            return new ParserIrAdapterMetadata(
                "scope-tree-sample-adapter",
                Set.of("1.0"),
                Set.of(ParserIrFeature.SCOPE_TREE, ParserIrFeature.SCOPE_EVENTS)
            );
        }

        @Override
        public ParserIrDocument parseToIr(ParseRequest request) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "Sample::Start");
            node.put("kind", "RuleDecl");
            node.put("span", Map.of("start", 0L, "end", (long) request.content().length()));
            List<Object> nodes = List.of(node);

            String mode = String.valueOf(request.options().getOrDefault("scopeMode", "lexical"));
            Map<String, String> scopeModeByNodeId = Map.of("Sample::Start", mode);
            List<Object> scopeEvents = ParserIrScopeEvents.emitSyntheticEnterLeaveEvents(scopeModeByNodeId, nodes);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("irVersion", "1.0");
            payload.put("source", request.sourceId());
            payload.put("nodes", nodes);
            payload.put("scopeEvents", scopeEvents);
            payload.put("diagnostics", List.of());
            return new ParserIrDocument(payload);
        }
    }
}
