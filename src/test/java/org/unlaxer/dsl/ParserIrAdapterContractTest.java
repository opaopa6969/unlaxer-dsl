package org.unlaxer.dsl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.unlaxer.dsl.ir.ParseRequest;
import org.unlaxer.dsl.ir.ParserIrAdapter;
import org.unlaxer.dsl.ir.ParserIrAdapterMetadata;
import org.unlaxer.dsl.ir.ParserIrConformanceValidator;
import org.unlaxer.dsl.ir.ParserIrDocument;
import org.unlaxer.dsl.ir.ParserIrFeature;

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
}
