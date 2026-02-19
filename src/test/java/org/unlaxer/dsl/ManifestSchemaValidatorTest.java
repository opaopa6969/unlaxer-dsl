package org.unlaxer.dsl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ManifestSchemaValidatorTest {

    @Test
    public void testAcceptsJsonManifest() {
        String payload = "{\"mode\":\"generate\",\"generatedAt\":\"2026-01-01T00:00:00Z\",\"toolVersion\":\"dev\","
            + "\"argsHash\":\"hash\",\"ok\":true,\"failReasonCode\":null,\"exitCode\":0,\"warningsCount\":0,"
            + "\"writtenCount\":1,\"skippedCount\":0,\"conflictCount\":0,\"dryRunCount\":0,"
            + "\"files\":[{\"action\":\"written\",\"path\":\"out/A.java\"}]}";
        ManifestSchemaValidator.validate("json", payload);
    }

    @Test
    public void testAcceptsNdjsonManifest() {
        String payload = "{\"event\":\"file\",\"action\":\"written\",\"path\":\"out/A.java\"}\n"
            + "{\"event\":\"manifest-summary\",\"mode\":\"generate\",\"generatedAt\":\"2026-01-01T00:00:00Z\","
            + "\"toolVersion\":\"dev\",\"argsHash\":\"hash\",\"ok\":false,\"failReasonCode\":\"FAIL_ON_CONFLICT\","
            + "\"exitCode\":4,\"warningsCount\":0,\"writtenCount\":0,\"skippedCount\":0,\"conflictCount\":1,\"dryRunCount\":0}";
        ManifestSchemaValidator.validate("ndjson", payload);
    }

    @Test
    public void testRejectsNdjsonWithoutSummary() {
        try {
            ManifestSchemaValidator.validate("ndjson", "{\"event\":\"file\",\"action\":\"written\",\"path\":\"out/A.java\"}");
            fail("expected schema validation error");
        } catch (ReportSchemaValidationException expected) {
            assertEquals("E-MANIFEST-SCHEMA-SUMMARY", expected.code());
        }
    }
}
